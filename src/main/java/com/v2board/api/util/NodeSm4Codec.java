package com.v2board.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Node API SM4 helper: working key from {@code server_token} / ApiKey, query {@code e}, body envelopes.
 */
@Component
public class NodeSm4Codec {

    private final ObjectMapper objectMapper;

    public NodeSm4Codec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** {@code SHA-256(UTF-8(token))[0:16]} — same as v2node {@code ApiKey} derivation. */
    public static byte[] deriveWorkingKey(String serverToken) {
        if (!StringUtils.hasText(serverToken)) {
            throw new IllegalArgumentException("server_token is empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serverToken.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(hash, 16);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String encryptIdentityQuery(String serverToken, long nodeId, String typeCode) {
        byte[] key = deriveWorkingKey(serverToken);
        try {
            Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("k", serverToken);
            identity.put("i", nodeId);
            identity.put("t", typeCode);
            return Sm4Util.encryptToCompact(objectMapper.writeValueAsString(identity), key);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt identity query failed: " + e.getMessage(), e);
        }
    }

    public NodeIdentity decryptIdentityQuery(String compactE, byte[] workingKey) {
        try {
            String json = Sm4Util.decryptFromCompact(compactE, workingKey);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object k = map.get("k");
            Object i = map.get("i");
            Object t = map.get("t");
            if (k == null || i == null || t == null) {
                throw new IllegalArgumentException("identity missing k/i/t");
            }
            long nodeId;
            if (i instanceof Number num) {
                nodeId = num.longValue();
            } else {
                nodeId = Long.parseLong(String.valueOf(i));
            }
            return new NodeIdentity(String.valueOf(k), nodeId, String.valueOf(t));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid identity ciphertext", e);
        }
    }

    public Map<String, String> encryptBody(Object data, byte[] workingKey) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return Sm4Util.encryptToEnvelope(json, workingKey);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt node body failed: " + e.getMessage(), e);
        }
    }

    public String decryptBodyToJson(Map<String, ?> envelope, byte[] workingKey) {
        if (envelope == null) {
            throw new IllegalArgumentException("body envelope is null");
        }
        Object iv = envelope.get("iv");
        Object payload = envelope.get("payload");
        if (iv == null || payload == null) {
            throw new IllegalArgumentException("body envelope missing iv/payload");
        }
        return Sm4Util.decryptFromEnvelope(String.valueOf(iv), String.valueOf(payload), workingKey);
    }

    public record NodeIdentity(String k, long nodeId, String typeCode) {
    }
}
