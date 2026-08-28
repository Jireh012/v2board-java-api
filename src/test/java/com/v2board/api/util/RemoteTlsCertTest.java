package com.v2board.api.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteTlsCertTest {

    @Test
    void generateRemoteTlsCertificate_pinMatchesDerSha256() throws Exception {
        Map<String, String> cert = Helper.generateRemoteTlsCertificate("node.example.com");
        String pem = cert.get("tls_cert");
        assertTrue(pem.contains("BEGIN CERTIFICATE"));
        assertTrue(cert.get("tls_key").contains("PRIVATE KEY"));

        X509Certificate parsed = parsePem(pem);
        assertTrue(parsed.getSubjectX500Principal().getName().contains("node.example.com"));
        byte[] der = parsed.getEncoded();
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest(der)) {
            hex.append(String.format("%02x", b));
        }
        assertEquals(hex.toString(), cert.get("pinned_peer_cert_sha256"));
        assertEquals(64, cert.get("pinned_peer_cert_sha256").length());
    }

    @Test
    void ensureRemoteTlsCertificate_isIdempotentWhenPinPresent() {
        Map<String, Object> tls = new HashMap<>();
        tls.put("cert_mode", "remote");
        tls.put("server_name", "a.example.com");
        Helper.ensureRemoteTlsCertificate(tls);
        String pin = String.valueOf(tls.get("pinned_peer_cert_sha256"));
        String pem = String.valueOf(tls.get("tls_cert"));
        assertFalse(pin.isBlank());

        tls.put("server_name", "b.example.com");
        Helper.ensureRemoteTlsCertificate(tls);
        assertEquals(pin, tls.get("pinned_peer_cert_sha256"));
        assertEquals(pem, tls.get("tls_cert"));
    }

    @Test
    void ensureRemoteTlsCertificate_skipsNonRemoteMode() {
        Map<String, Object> tls = new HashMap<>();
        tls.put("cert_mode", "dns");
        Helper.ensureRemoteTlsCertificate(tls);
        assertEquals(null, tls.get("tls_cert"));
    }

    @Test
    void twoGenerationsDiffer() {
        Map<String, String> a = Helper.generateRemoteTlsCertificate("example.com");
        Map<String, String> b = Helper.generateRemoteTlsCertificate("example.com");
        assertNotEquals(a.get("pinned_peer_cert_sha256"), b.get("pinned_peer_cert_sha256"));
    }

    private static X509Certificate parsePem(String pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }
}
