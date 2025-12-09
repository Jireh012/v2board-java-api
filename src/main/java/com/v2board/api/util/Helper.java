package com.v2board.api.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

public class Helper {
    
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
}

