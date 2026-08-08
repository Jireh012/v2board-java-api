package com.v2board.api.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SM4-CBC + PKCS7 for public config envelope ({@code iv}, {@code payload} base64).
 */
public final class Sm4Util {

    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final int BLOCK = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Util() {
    }

    /**
     * Parse configured key: 32 hex chars → 16 bytes; otherwise UTF-8 must be exactly 16 bytes.
     */
    public static byte[] parseKey(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("SM4 key is empty");
        }
        String trimmed = configured.trim();
        if (trimmed.matches("(?i)^[0-9a-f]{32}$")) {
            return hexToBytes(trimmed);
        }
        byte[] utf8 = trimmed.getBytes(StandardCharsets.UTF_8);
        if (utf8.length != BLOCK) {
            throw new IllegalArgumentException("SM4 key must be 16 UTF-8 bytes or 32 hex chars");
        }
        return utf8;
    }

    public static Map<String, String> encryptToEnvelope(String plaintextUtf8, byte[] key) {
        try {
            byte[] iv = new byte[BLOCK];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            byte[] cipherBytes = cipher.doFinal(plaintextUtf8.getBytes(StandardCharsets.UTF_8));
            Map<String, String> envelope = new LinkedHashMap<>();
            envelope.put("iv", Base64.getEncoder().encodeToString(iv));
            envelope.put("payload", Base64.getEncoder().encodeToString(cipherBytes));
            return envelope;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SM4 encrypt failed: " + e.getMessage(), e);
        }
    }

    public static String decryptFromEnvelope(String ivBase64, String payloadBase64, byte[] key) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] payload = Base64.getDecoder().decode(payloadBase64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(payload);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SM4 decrypt failed: " + e.getMessage(), e);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}
