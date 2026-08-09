package com.v2board.api.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ExternalSubscribeParser {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSubscribeParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> NON_PROXY_TYPES = Set.of(
            "direct", "block", "dns", "selector", "urltest", "断路器",
            "mixed", "tun", "redirect", "tproxy", "shadowtls", "relay", "chain", "tor", "ssh"
    );

    public List<CanonicalExternalNode> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String trimmed = content.trim();
        List<CanonicalExternalNode> nodes = tryParseSingbox(trimmed);
        if (!nodes.isEmpty()) {
            return dedupe(nodes);
        }
        nodes = tryParseClash(trimmed);
        if (!nodes.isEmpty()) {
            return dedupe(nodes);
        }
        nodes = tryParseUriList(trimmed);
        return dedupe(nodes);
    }

    @SuppressWarnings("unchecked")
    private List<CanonicalExternalNode> tryParseSingbox(String content) {
        if (!content.startsWith("{") && !content.startsWith("[")) {
            return List.of();
        }
        try {
            Object root = MAPPER.readValue(content, Object.class);
            List<Map<String, Object>> outbounds = new ArrayList<>();
            if (root instanceof Map<?, ?> map) {
                Object obs = map.get("outbounds");
                if (obs instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            outbounds.add((Map<String, Object>) m);
                        }
                    }
                }
            } else if (root instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        outbounds.add((Map<String, Object>) m);
                    }
                }
            }
            List<CanonicalExternalNode> result = new ArrayList<>();
            for (Map<String, Object> outbound : outbounds) {
                CanonicalExternalNode node = fromSingboxOutbound(outbound);
                if (node != null) {
                    result.add(node);
                }
            }
            return result;
        } catch (Exception e) {
            logger.debug("Not sing-box JSON: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<CanonicalExternalNode> tryParseClash(String content) {
        String lower = content.toLowerCase();
        if (!lower.contains("proxies:") && !lower.contains("proxy-groups:")) {
            // still try YAML if it looks like yaml map
            if (!content.contains("\n") || content.startsWith("vmess://") || content.startsWith("ss://")) {
                return List.of();
            }
        }
        try {
            Yaml yaml = new Yaml();
            Object root = yaml.load(content);
            if (!(root instanceof Map<?, ?> map)) {
                return List.of();
            }
            Object proxiesObj = map.get("proxies");
            if (!(proxiesObj instanceof List<?> proxies) || proxies.isEmpty()) {
                return List.of();
            }
            List<CanonicalExternalNode> result = new ArrayList<>();
            for (Object item : proxies) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> proxy = (Map<String, Object>) m;
                Map<String, Object> outbound = ClashProxyConverter.clashToSingbox(proxy);
                if (outbound == null) {
                    continue;
                }
                CanonicalExternalNode node = new CanonicalExternalNode();
                node.setName(str(outbound.get("tag")));
                node.setProtocol(str(outbound.get("type")));
                node.setSingboxOutbound(outbound);
                node.setShareUri(ShareUriConverter.singboxToUri(outbound));
                node.setFingerprint(ExternalNodeIdentity.fingerprint(outbound));
                result.add(node);
            }
            return result;
        } catch (Exception e) {
            logger.debug("Not Clash YAML: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CanonicalExternalNode> tryParseUriList(String content) {
        String text = content.trim();
        // maybe whole body is base64
        if (!text.contains("://")) {
            String decoded = tryDecodeBase64(text);
            if (decoded != null && decoded.contains("://")) {
                text = decoded;
            }
        }
        List<CanonicalExternalNode> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String uri = line.trim();
            if (uri.isEmpty() || uri.startsWith("#")) {
                continue;
            }
            // line itself may be base64 of single uri
            if (!uri.contains("://")) {
                String decoded = tryDecodeBase64(uri);
                if (decoded != null) {
                    uri = decoded.trim();
                }
            }
            if (!uri.contains("://")) {
                continue;
            }
            // sometimes multiple uris space-separated
            for (String part : uri.split("\\s+")) {
                if (!part.contains("://")) {
                    continue;
                }
                Map<String, Object> outbound = ShareUriConverter.uriToSingbox(part);
                if (outbound == null) {
                    continue;
                }
                CanonicalExternalNode node = new CanonicalExternalNode();
                node.setName(str(outbound.get("tag")));
                node.setProtocol(str(outbound.get("type")));
                node.setShareUri(part);
                node.setSingboxOutbound(outbound);
                node.setFingerprint(ExternalNodeIdentity.fingerprint(outbound));
                result.add(node);
            }
        }
        return result;
    }

    private CanonicalExternalNode fromSingboxOutbound(Map<String, Object> outbound) {
        if (outbound == null) {
            return null;
        }
        String type = str(outbound.get("type")).toLowerCase();
        if (type.isEmpty() || NON_PROXY_TYPES.contains(type)) {
            return null;
        }
        if (outbound.get("server") == null || outbound.get("server_port") == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(outbound);
        if (copy.get("tag") == null || str(copy.get("tag")).isBlank()) {
            copy.put("tag", type + "-" + copy.get("server") + "-" + copy.get("server_port"));
        }
        CanonicalExternalNode node = new CanonicalExternalNode();
        node.setName(str(copy.get("tag")));
        node.setProtocol(type);
        node.setSingboxOutbound(copy);
        node.setShareUri(ShareUriConverter.singboxToUri(copy));
        node.setFingerprint(ExternalNodeIdentity.fingerprint(copy));
        return node;
    }

    private List<CanonicalExternalNode> dedupe(List<CanonicalExternalNode> nodes) {
        Map<String, CanonicalExternalNode> map = new LinkedHashMap<>();
        for (CanonicalExternalNode node : nodes) {
            if (node.getFingerprint() == null) {
                continue;
            }
            map.putIfAbsent(node.getFingerprint(), node);
        }
        return new ArrayList<>(map.values());
    }

    private static String tryDecodeBase64(String raw) {
        try {
            String s = raw.replace("-", "+").replace("_", "/").replaceAll("\\s", "");
            int mod = s.length() % 4;
            if (mod > 0) {
                s = s + "====".substring(mod);
            }
            byte[] decoded = Base64.getDecoder().decode(s);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
