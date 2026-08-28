package com.v2board.api.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class Helper {

    /**
     * 对齐 PHP Helper::guid($format)
     * format=true 返回标准 UUID；否则返回 32 位十六进制字符串（用于 token）
     */
    public static String guid(boolean format) {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        if (format) {
            return uuid.toString();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(uuid.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            return uuid.toString().replace("-", "");
        }
    }

    public static String guid() {
        return guid(false);
    }

    public static boolean emailSuffixVerify(String email, Object suffixs) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String suffix = email.substring(email.indexOf('@') + 1).toLowerCase();
        java.util.List<String> list;
        if (suffixs instanceof java.util.Collection<?> col) {
            list = col.stream().map(String::valueOf).map(String::toLowerCase).toList();
        } else if (suffixs instanceof String s) {
            list = java.util.Arrays.stream(s.split(",")).map(String::trim).map(String::toLowerCase).toList();
        } else {
            return false;
        }
        return list.contains(suffix);
    }
    
    /**
     * UUID 转 Base64
     */
    public static String uuidToBase64(String uuid, int length) {
        String substr = uuid.substring(0, Math.min(length, uuid.length()));
        return Base64.getEncoder().encodeToString(substr.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 获取服务器密钥
     */
    public static String getServerKey(long timestamp, int length) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8));
            String hashStr = bytesToHex(hash);
            String substr = hashStr.substring(0, Math.min(length, hashStr.length()));
            return Base64.getEncoder().encodeToString(substr.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 生成订单号
     * 对齐 PHP Helper::generateOrderNo():
     *   date('YmdHms') . substr(microtime(), 2, 6) . mt_rand(10000, 99999)
     */
    public static String generateOrderNo() {
        // 时间部分：yyyyMMddHHmmss（14 位）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String timePart = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 微秒部分：取当前纳秒的低 6 位数字
        long micros = System.nanoTime() % 1_000_000L;
        String microPart = String.format("%06d", micros);
        // 随机部分：5 位随机数
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000, 100000);
        String randomPart = String.format("%05d", random);
        return timePart + microPart + randomPart;
    }
    
    /**
     * 随机端口（处理端口范围）
     */
    public static int randomPort(String portStr) {
        if (portStr == null || portStr.isEmpty()) {
            return 0;
        }
        if (portStr.contains("-")) {
            String[] range = portStr.split("-");
            int min = Integer.parseInt(range[0].trim());
            int max = Integer.parseInt(range[1].trim());
            return new Random().nextInt(max - min + 1) + min;
        }
        return Integer.parseInt(portStr.trim());
    }
    
    /**
     * 流量转换
     */
    public static String trafficConvert(long bytes) {
        double kb = 1024;
        double mb = 1048576;
        double gb = 1073741824;
        
        if (bytes > gb) {
            return String.format("%.2f GB", bytes / gb);
        } else if (bytes > mb) {
            return String.format("%.2f MB", bytes / mb);
        } else if (bytes > kb) {
            return String.format("%.2f KB", bytes / kb);
        } else if (bytes < 0) {
            return "0 B";
        } else {
            return String.format("%.2f B", (double) bytes);
        }
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Base64 URL安全编码
     * PHP: base64EncodeUrlSafe($data)
     */
    public static String base64EncodeUrlSafe(byte[] data) {
        String encoded = Base64.getEncoder().encodeToString(data);
        return encoded.replace("+", "-").replace("/", "_").replace("=", "");
    }
    
    /**
     * Base64 URL安全编码（字符串）
     */
    public static String base64EncodeUrlSafe(String data) {
        return base64EncodeUrlSafe(data.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Base64 URL安全解码
     * PHP: base64DecodeUrlSafe($data)
     */
    public static String base64DecodeUrlSafe(String data) {
        String b64 = data.replace("-", "+").replace("_", "/");
        int pad = 4 - (b64.length() % 4);
        if (pad < 4) {
            b64 += "=".repeat(pad);
        }
        byte[] decoded = Base64.getDecoder().decode(b64);
        return new String(decoded, StandardCharsets.UTF_8);
    }
    
    /**
     * 生成订阅URL
     * PHP: Helper::getSubscribeUrl($token)
     * 
     * @param token 用户token
     * @param userId 用户ID（用于方法2）
     * @param subMethod 订阅方法：0-直接token，1-OTP，2-TOTP
     * @param subscribePath 订阅路径
     * @param subscribeUrls 订阅URL列表（逗号分隔）
     * @param subscribeExpire TOTP过期时间（分钟）
     * @return 订阅URL
     */
    public static String getSubscribeUrl(String token, Long userId, 
                                         Integer subMethod, String subscribePath,
                                         String subscribeUrls, Integer subscribeExpire) {
        if (subscribePath == null || subscribePath.isEmpty()) {
            subscribePath = "/api/v1/client/subscribe";
        } else {
            // 统一路径格式，保证与 DynamicRouteConfig 一致
            if (!subscribePath.startsWith("/")) {
                subscribePath = "/" + subscribePath;
            }
        }
        
        String[] urlArray = subscribeUrls != null && !subscribeUrls.isEmpty() 
            ? subscribeUrls.split(",") 
            : new String[0];
        String subscribeUrl = urlArray.length > 0 
            ? urlArray[new Random().nextInt(urlArray.length)].trim() 
            : "";
        
        String path;
        switch (subMethod != null ? subMethod : 0) {
            case 0:  // 直接使用token
                path = subscribePath + "?token=" + token;
                break;
            case 1:  // OTP方式（需要缓存支持，这里简化处理）
                // 注意：实际实现需要Redis缓存支持
                path = subscribePath + "?token=" + token;
                break;
            case 2:  // TOTP方式
                if (userId == null) {
                    path = subscribePath + "?token=" + token;
                } else {
                    int timestep = (subscribeExpire != null ? subscribeExpire : 5) * 60;
                    long counter = System.currentTimeMillis() / 1000 / timestep;
                    
                    // 构建counterBytes: pack('N*', 0) . pack('N*', $counter)
                    ByteBuffer buffer = ByteBuffer.allocate(8);
                    buffer.order(ByteOrder.BIG_ENDIAN);
                    buffer.putInt(0);
                    buffer.putInt((int)counter);
                    byte[] counterBytes = buffer.array();
                    
                    // HMAC-SHA1
                    try {
                        Mac mac = Mac.getInstance("HmacSHA1");
                        SecretKeySpec secretKey = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
                        mac.init(secretKey);
                        byte[] hash = mac.doFinal(counterBytes);
                        String hashStr = bytesToHex(hash);
                        
                        String newtoken = base64EncodeUrlSafe(userId + ":" + hashStr);
                        path = subscribePath + "?token=" + newtoken;
                    } catch (Exception e) {
                        // 如果出错，回退到直接token方式
                        path = subscribePath + "?token=" + token;
                    }
                }
                break;
            default:
                path = subscribePath + "?token=" + token;
                break;
        }
        
        if (!subscribeUrl.isEmpty()) {
            return subscribeUrl + path;
        }
        return path;
    }

    public static String formatHost(String host) {
        if (host == null) {
            return "";
        }
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }

    public static String encodeURIComponent(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 对齐 PHP Helper::buildUri — v2node 按 protocol 分发
     */
    public static String buildUri(String uuid, Map<String, Object> server) {
        if (server == null) {
            return "";
        }
        String type = String.valueOf(server.get("type"));
        if ("v2node".equals(type)) {
            Object protocol = server.get("protocol");
            if (protocol != null) {
                type = String.valueOf(protocol);
            }
        }
        return switch (type) {
            case "shadowsocks" -> buildShadowsocksUri(uuid, server);
            case "vmess" -> ""; // GeneralHandler 已有实现
            case "vless" -> "";
            case "trojan" -> buildTrojanUri(uuid, server);
            case "hysteria", "hysteria2" -> buildHysteria2Uri(uuid, server);
            case "tuic" -> buildTuicUri(uuid, server);
            case "anytls" -> buildAnytlsUri(uuid, server);
            default -> "";
        };
    }

    @SuppressWarnings("unchecked")
    public static String buildAnytlsUri(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = server.get("tls_settings") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : (server.get("tlsSettings") instanceof Map<?, ?> m2 ? (Map<String, Object>) m2 : Map.of());
        Map<String, String> config = new LinkedHashMap<>();
        config.put("type", server.get("network") != null ? String.valueOf(server.get("network")) : "tcp");
        Object insecure = server.get("insecure");
        if (insecure == null && tlsSettings.get("allow_insecure") != null) {
            insecure = tlsSettings.get("allow_insecure");
        }
        config.put("insecure", String.valueOf(insecure != null ? insecure : 0));
        if (tlsSettings.get("fingerprint") != null) {
            config.put("fp", String.valueOf(tlsSettings.get("fingerprint")));
        } else {
            config.put("fp", "chrome");
        }
        config.put("pcs", pinnedPeerCertSha256(server));
        if (server.get("server_name") != null) {
            config.put("sni", String.valueOf(server.get("server_name")));
        } else if (tlsSettings.get("server_name") != null) {
            config.put("sni", String.valueOf(tlsSettings.get("server_name")));
        }
        Object tls = server.get("tls");
        if (tls instanceof Number n && n.intValue() == 2) {
            config.put("security", "reality");
            if (tlsSettings.get("public_key") != null) {
                config.put("pbk", String.valueOf(tlsSettings.get("public_key")));
            }
            if (tlsSettings.get("short_id") != null) {
                config.put("sid", String.valueOf(tlsSettings.get("short_id")));
            }
        }
        if (server.get("network") != null && server.get("network_settings") != null) {
            appendNetworkQuery(server, config);
        } else if (server.get("network") != null && server.get("networkSettings") != null) {
            Map<String, Object> copy = new LinkedHashMap<>(server);
            copy.put("network_settings", server.get("networkSettings"));
            appendNetworkQuery(copy, config);
        }
        String remote = formatHost(String.valueOf(server.get("host")));
        String port = String.valueOf(server.get("port"));
        String name = encodeURIComponent(String.valueOf(server.get("name")));
        return "anytls://" + password + "@" + remote + ":" + port + "/?" + toQuery(config) + "#" + name + "\r\n";
    }

    @SuppressWarnings("unchecked")
    public static String buildTuicUri(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = server.get("tls_settings") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        Map<String, String> config = new LinkedHashMap<>();
        if (server.get("server_name") != null) {
            config.put("sni", String.valueOf(server.get("server_name")));
        } else if (tlsSettings.get("server_name") != null) {
            config.put("sni", String.valueOf(tlsSettings.get("server_name")));
        }
        config.put("alpn", "h3");
        if (server.get("congestion_control") != null) {
            config.put("congestion_control", String.valueOf(server.get("congestion_control")));
        }
        Object insecure = server.get("insecure");
        if (insecure == null && tlsSettings.get("allow_insecure") != null) {
            insecure = tlsSettings.get("allow_insecure");
        }
        config.put("allow_insecure", String.valueOf(insecure != null ? insecure : 0));
        if (server.get("disable_sni") != null) {
            config.put("disable_sni", String.valueOf(server.get("disable_sni")));
        }
        if (server.get("udp_relay_mode") != null) {
            config.put("udp_relay_mode", String.valueOf(server.get("udp_relay_mode")));
        }
        config.put("pcs", pinnedPeerCertSha256(server));
        String remote = formatHost(String.valueOf(server.get("host")));
        String port = String.valueOf(server.get("port"));
        String name = encodeURIComponent(String.valueOf(server.get("name")));
        return "tuic://" + password + ":" + password + "@" + remote + ":" + port + "?" + toQuery(config) + "#" + name + "\r\n";
    }

    @SuppressWarnings("unchecked")
    public static String buildHysteria2Uri(String password, Map<String, Object> server) {
        Map<String, Object> tlsSettings = server.get("tls_settings") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String remote = formatHost(String.valueOf(server.get("host")));
        String name = encodeURIComponent(String.valueOf(server.get("name")));
        String portField = String.valueOf(server.get("port"));
        String[] parts = portField.split(",");
        String firstPort = parts[0];
        if (firstPort.contains("-")) {
            firstPort = firstPort.split("-")[0];
        }
        Object insecure = tlsSettings.getOrDefault("allow_insecure", 0);
        String sni = tlsSettings.get("server_name") != null ? String.valueOf(tlsSettings.get("server_name")) : "";
        StringBuilder uri = new StringBuilder();
        uri.append("hysteria2://").append(password).append("@").append(remote).append(":").append(firstPort)
                .append("/?insecure=").append(insecure).append("&sni=").append(sni)
                .append("&pcs=").append(pinnedPeerCertSha256(server));
        if (server.get("obfs") != null && server.get("obfs_password") != null) {
            uri.append("&obfs=").append(server.get("obfs"))
                    .append("&obfs-password=").append(encodeURIComponent(String.valueOf(server.get("obfs_password"))));
        }
        if (parts.length != 1 || parts[0].contains("-")) {
            Object mport = server.get("mport");
            if (mport != null) {
                uri.append("&mport=").append(mport);
            }
        }
        return uri + "#" + name + "\r\n";
    }

    public static String buildHysteriaUri(String password, Map<String, Object> server) {
        Object version = server.get("version");
        if (version instanceof Number n && n.intValue() == 2) {
            return buildHysteria2Uri(password, server);
        }
        String remote = formatHost(String.valueOf(server.get("host")));
        String name = encodeURIComponent(String.valueOf(server.get("name")));
        String portField = String.valueOf(server.get("port"));
        String[] parts = portField.split(",");
        String firstPort = parts[0];
        if (firstPort.contains("-")) {
            firstPort = firstPort.split("-")[0];
        }
        Object insecure = server.getOrDefault("insecure", 0);
        String sni = server.get("server_name") != null ? String.valueOf(server.get("server_name")) : "";
        StringBuilder uri = new StringBuilder();
        uri.append("hysteria://").append(remote).append(":").append(firstPort)
                .append("/?protocol=udp&auth=").append(password)
                .append("&insecure=").append(insecure).append("&peer=").append(sni)
                .append("&upmbps=").append(server.getOrDefault("down_mbps", 0))
                .append("&downmbps=").append(server.getOrDefault("up_mbps", 0));
        if (server.get("obfs") != null && server.get("obfs_password") != null) {
            uri.append("&obfs=").append(server.get("obfs"))
                    .append("&obfsParam").append(encodeURIComponent(String.valueOf(server.get("obfs_password"))));
        }
        if (parts.length != 1 || parts[0].contains("-")) {
            Object mport = server.get("mport");
            if (mport != null) {
                uri.append("&mport=").append(mport);
            }
        }
        return uri + "#" + name + "\r\n";
    }

    public static String buildTrojanUri(String password, Map<String, Object> server) {
        String host = formatHost(String.valueOf(server.get("host")));
        int port = server.get("port") instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(server.get("port")));
        String serverName = server.get("server_name") != null ? String.valueOf(server.get("server_name")) : "";
        Object allowInsecure = server.getOrDefault("allow_insecure", false);
        String name = encodeURIComponent(String.valueOf(server.get("name")));
        String query = "allowInsecure=" + allowInsecure + "&peer=" + serverName + "&sni=" + serverName
                + "&pcs=" + encodeURIComponent(pinnedPeerCertSha256(server));
        return "trojan://" + password + "@" + host + ":" + port + "?" + query + "#" + name + "\r\n";
    }

    public static String buildShadowsocksUri(String uuid, Map<String, Object> server) {
        return "";
    }

    @SuppressWarnings("unchecked")
    private static void appendNetworkQuery(Map<String, Object> server, Map<String, String> config) {
        String network = String.valueOf(server.get("network"));
        Object settingsObj = server.get("network_settings");
        if (!(settingsObj instanceof Map<?, ?>)) {
            settingsObj = server.get("networkSettings");
        }
        if (!(settingsObj instanceof Map<?, ?> settings)) {
            return;
        }
        Map<String, Object> ns = (Map<String, Object>) settings;
        switch (network) {
            case "ws" -> {
                if (ns.get("path") != null) config.put("path", String.valueOf(ns.get("path")));
                if (ns.get("headers") instanceof Map<?, ?> headers && headers.get("Host") != null) {
                    config.put("host", String.valueOf(headers.get("Host")));
                }
            }
            case "grpc" -> {
                if (ns.get("serviceName") != null) config.put("serviceName", String.valueOf(ns.get("serviceName")));
            }
            case "xhttp" -> {
                if (ns.get("path") != null) config.put("path", String.valueOf(ns.get("path")));
                if (ns.get("host") != null) config.put("host", String.valueOf(ns.get("host")));
                config.put("mode", ns.get("mode") != null ? String.valueOf(ns.get("mode")) : "auto");
                if (ns.get("extra") != null) {
                    try {
                        config.put("extra", new ObjectMapper().writeValueAsString(ns.get("extra")));
                    } catch (Exception ignored) {
                    }
                }
            }
            default -> {
            }
        }
    }

    private static String toQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            first = false;
            sb.append(encodeURIComponent(e.getKey())).append('=').append(encodeURIComponent(e.getValue()));
        }
        return sb.toString();
    }

    /**
     * 生成 Reality / X25519 密钥对（对齐 PHP SodiumCompat::crypto_box_keypair + url-safe base64）。
     * 返回 public_key / private_key / short_id（sha1(private_key) 前 8 位）。
     */
    public static Map<String, String> generateRealityKeyPair() {
        X25519KeyPairGenerator gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var kp = gen.generateKeyPair();
        byte[] privateKey = ((X25519PrivateKeyParameters) kp.getPrivate()).getEncoded();
        byte[] publicKey = ((X25519PublicKeyParameters) kp.getPublic()).getEncoded();
        String privateKeyB64 = base64EncodeUrlSafe(privateKey);
        String publicKeyB64 = base64EncodeUrlSafe(publicKey);
        String shortId;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(privateKeyB64.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            shortId = hex.substring(0, 8);
        } catch (Exception e) {
            shortId = privateKeyB64.substring(0, Math.min(8, privateKeyB64.length()));
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("public_key", publicKeyB64);
        result.put("private_key", privateKeyB64);
        result.put("short_id", shortId);
        return result;
    }

    /**
     * 生成 ECH 密钥对（对齐 PHP Helper::generateEchKeyPair）
     */
    public static Map<String, String> generateEchKeyPair(String outerSni) {
        X25519KeyPairGenerator gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var kp = gen.generateKeyPair();
        byte[] privateKey = ((X25519PrivateKeyParameters) kp.getPrivate()).getEncoded();
        byte[] publicKey = ((X25519PublicKeyParameters) kp.getPublic()).getEncoded();

        int configId = new SecureRandom().nextInt(256);
        byte[] suites = new byte[] {0x00, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01, 0x00, 0x03};
        byte[] outerBytes = outerSni.getBytes(StandardCharsets.UTF_8);

        ByteBuffer configData = ByteBuffer.allocate(256);
        configData.put((byte) configId);
        configData.putShort((short) 0x0020);
        configData.putShort((short) 32);
        configData.put(publicKey);
        configData.putShort((short) suites.length);
        configData.put(suites);
        configData.put((byte) 0);
        configData.put((byte) outerBytes.length);
        configData.put(outerBytes);
        configData.putShort((short) 0);
        configData.flip();
        byte[] configBytes = new byte[configData.remaining()];
        configData.get(configBytes);

        ByteBuffer echConfig = ByteBuffer.allocate(4 + configBytes.length);
        echConfig.putShort((short) 0xfe0d);
        echConfig.putShort((short) configBytes.length);
        echConfig.put(configBytes);
        echConfig.flip();
        byte[] echConfigBytes = new byte[echConfig.remaining()];
        echConfig.get(echConfigBytes);

        ByteBuffer echKeys = ByteBuffer.allocate(2 + echConfigBytes.length + 2 + 1 + 2 + 32);
        echKeys.putShort((short) echConfigBytes.length);
        echKeys.put(echConfigBytes);
        echKeys.putShort((short) 1);
        echKeys.put((byte) configId);
        echKeys.putShort((short) 32);
        echKeys.put(privateKey);
        echKeys.flip();
        byte[] echKeysBytes = new byte[echKeys.remaining()];
        echKeys.get(echKeysBytes);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("ech_key", Base64.getEncoder().encodeToString(echKeysBytes));
        result.put("ech_config", Base64.getEncoder().encodeToString(echConfigBytes));
        return result;
    }

    /**
     * 对齐 PHP V2nodeController：tls_settings.cert_mode=remote 且尚未写入 PIN 时生成 P-256 自签。
     * PIN 已存在（isset）则不重签。
     */
    public static void ensureRemoteTlsCertificate(Map<String, Object> tlsSettings) {
        if (tlsSettings == null) {
            return;
        }
        if (!"remote".equals(String.valueOf(tlsSettings.get("cert_mode")))) {
            return;
        }
        if (tlsSettings.get("pinned_peer_cert_sha256") != null) {
            return;
        }
        Object sni = tlsSettings.get("server_name");
        String cn = (sni == null || String.valueOf(sni).isBlank()) ? "example.com" : String.valueOf(sni);
        Map<String, String> cert = generateRemoteTlsCertificate(cn);
        tlsSettings.put("tls_cert", cert.get("tls_cert"));
        tlsSettings.put("tls_key", cert.get("tls_key"));
        tlsSettings.put("pinned_peer_cert_sha256", cert.get("pinned_peer_cert_sha256"));
    }

    /**
     * 对齐 PHP OpenSSL EC P-256 自签：CN=SNI，3650 天，SHA-256，PIN = SHA-256(DER) hex。
     */
    public static Map<String, String> generateRemoteTlsCertificate(String serverName) {
        String cn = (serverName == null || serverName.isBlank()) ? "example.com" : serverName.trim();
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();

            X500Name subject = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, cn).build();
            Date notBefore = new Date();
            Date notAfter = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));
            BigInteger serial = new BigInteger(64, new SecureRandom()).abs();
            if (serial.equals(BigInteger.ZERO)) {
                serial = BigInteger.ONE;
            }

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject, serial, notBefore, notAfter, subject, kp.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(signer));

            String tlsCert = pemEncode("CERTIFICATE", cert.getEncoded());
            String tlsKey;
            try (StringWriter sw = new StringWriter(); JcaPEMWriter pw = new JcaPEMWriter(sw)) {
                pw.writeObject(kp.getPrivate());
                pw.flush();
                tlsKey = sw.toString();
            }
            String pin = bytesToHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));

            Map<String, String> result = new LinkedHashMap<>();
            result.put("tls_cert", tlsCert);
            result.put("tls_key", tlsKey);
            result.put("pinned_peer_cert_sha256", pin);
            return result;
        } catch (Exception e) {
            throw new BusinessException(500, "创建失败");
        }
    }

    private static String pemEncode(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> tlsSettingsOf(Map<String, Object> server) {
        if (server == null) {
            return Map.of();
        }
        Object tls = server.get("tls_settings");
        if (!(tls instanceof Map<?, ?>)) {
            tls = server.get("tlsSettings");
        }
        if (tls instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    public static String pinnedPeerCertSha256(Map<String, Object> server) {
        Object pin = tlsSettingsOf(server).get("pinned_peer_cert_sha256");
        return pin == null ? "" : String.valueOf(pin);
    }
}

