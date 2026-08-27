package com.v2board.api.service.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.mapper.ExternalSubscribeNodeMapper;
import com.v2board.api.mapper.ExternalSubscribeSourceMapper;
import com.v2board.api.model.ExternalSubscribeNode;
import com.v2board.api.model.ExternalSubscribeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExternalSubscribeNodeService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSubscribeNodeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExternalSubscribeNodeMapper nodeMapper;
    private final ExternalSubscribeSourceMapper sourceMapper;

    public ExternalSubscribeNodeService(ExternalSubscribeNodeMapper nodeMapper,
                                        ExternalSubscribeSourceMapper sourceMapper) {
        this.nodeMapper = nodeMapper;
        this.sourceMapper = sourceMapper;
    }

    /**
     * 订阅合并用：所有启用源下 reachable=1 的节点，跨源按逻辑键去重后转为 server map。
     */
    public List<Map<String, Object>> listReachableAsServerMaps() {
        List<ExternalSubscribeSource> enabled = sourceMapper.selectList(
                new LambdaQueryWrapper<ExternalSubscribeSource>().eq(ExternalSubscribeSource::getEnable, 1));
        if (enabled.isEmpty()) {
            return List.of();
        }
        Set<Long> sourceIds = enabled.stream()
                .filter(ExternalSubscribeTraffic::isDeliverable)
                .map(ExternalSubscribeSource::getId)
                .collect(Collectors.toSet());
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        List<ExternalSubscribeNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ExternalSubscribeNode>()
                        .in(ExternalSubscribeNode::getSourceId, sourceIds)
                        .eq(ExternalSubscribeNode::getReachable, 1)
                        .orderByAsc(ExternalSubscribeNode::getSort)
                        .orderByAsc(ExternalSubscribeNode::getId));
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (ExternalSubscribeNode node : nodes) {
            if (node.getSourceId() == null || !sourceIds.contains(node.getSourceId())) {
                continue;
            }
            if (ExternalInfoNode.isInfoName(node.getName())) {
                continue;
            }
            Map<String, Object> map = toServerMap(node);
            if (map != null) {
                mapped.add(map);
            }
        }
        return dedupeAndNumberNames(mapped);
    }

    /**
     * Cross-source logical-key dedupe (first wins) then colliding display-name numbering.
     * Package-visible for unit tests.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> dedupeAndNumberNames(List<Map<String, Object>> servers) {
        if (servers == null || servers.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> server : servers) {
            Object outboundObj = server.get("singbox_outbound");
            if (!(outboundObj instanceof Map<?, ?>)) {
                continue;
            }
            String key = ExternalNodeIdentity.logicalKey((Map<String, Object>) outboundObj);
            if (key.isBlank()) {
                continue;
            }
            byKey.putIfAbsent(key, server);
        }
        List<Map<String, Object>> deduped = new ArrayList<>(byKey.values());

        Map<String, Integer> nameCounts = new HashMap<>();
        for (Map<String, Object> server : deduped) {
            String name = displayName(server);
            if (!name.isEmpty()) {
                nameCounts.merge(name, 1, Integer::sum);
            }
        }
        Map<String, Integer> nameSeq = new HashMap<>();
        for (Map<String, Object> server : deduped) {
            String name = displayName(server);
            if (name.isEmpty()) {
                continue;
            }
            if (nameCounts.getOrDefault(name, 0) <= 1) {
                continue;
            }
            int n = nameSeq.merge(name, 1, Integer::sum);
            ExternalNodeIdentity.applyDisplayName(server, name + n);
        }
        return deduped;
    }

    private static String displayName(Map<String, Object> server) {
        Object raw = server.get("name");
        return raw == null ? "" : String.valueOf(raw);
    }

    public List<ExternalSubscribeNode> listBySourceId(Long sourceId) {
        List<ExternalSubscribeNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ExternalSubscribeNode>()
                .eq(ExternalSubscribeNode::getSourceId, sourceId)
                .orderByDesc(ExternalSubscribeNode::getReachable)
                .orderByAsc(ExternalSubscribeNode::getSort)
                .orderByAsc(ExternalSubscribeNode::getId));
        return nodes.stream()
                .filter(n -> !ExternalInfoNode.isInfoName(n.getName()))
                .collect(Collectors.toList());
    }

    public Map<Long, long[]> countBySourceIds(List<Long> sourceIds) {
        Map<Long, long[]> counts = new HashMap<>();
        if (sourceIds == null || sourceIds.isEmpty()) {
            return counts;
        }
        for (Long id : sourceIds) {
            counts.put(id, new long[]{0, 0});
        }
        List<ExternalSubscribeNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<ExternalSubscribeNode>().in(ExternalSubscribeNode::getSourceId, sourceIds));
        for (ExternalSubscribeNode node : nodes) {
            if (ExternalInfoNode.isInfoName(node.getName())) {
                continue;
            }
            long[] c = counts.computeIfAbsent(node.getSourceId(), k -> new long[]{0, 0});
            c[0]++;
            if (node.getReachable() != null && node.getReachable() == 1) {
                c[1]++;
            }
        }
        return counts;
    }

    public void deleteBySourceId(Long sourceId) {
        nodeMapper.delete(new LambdaQueryWrapper<ExternalSubscribeNode>()
                .eq(ExternalSubscribeNode::getSourceId, sourceId));
    }

    private Map<String, Object> toServerMap(ExternalSubscribeNode node) {
        try {
            Map<String, Object> outbound = MAPPER.readValue(node.getSingboxOutbound(), new TypeReference<>() {});
            Map<String, Object> clashProxy = ClashProxyConverter.singboxToClash(outbound);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("external", true);
            map.put("type", "external");
            map.put("name", node.getName());
            map.put("protocol", node.getProtocol());
            map.put("share_uri", node.getShareUri());
            map.put("singbox_outbound", outbound);
            if (clashProxy != null) {
                map.put("clash_proxy", clashProxy);
            }
            map.put("show", 1);
            map.put("sort", node.getSort() != null ? node.getSort() : 0);
            return map;
        } catch (Exception e) {
            logger.warn("Failed to map external node {}: {}", node.getId(), e.getMessage());
            return null;
        }
    }
}
