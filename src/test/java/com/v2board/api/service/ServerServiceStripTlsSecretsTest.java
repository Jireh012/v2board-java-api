package com.v2board.api.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerServiceStripTlsSecretsTest {

    @Test
    void stripSubscribeTlsSecrets_removesPemKeepsPin() {
        Map<String, Object> tls = new HashMap<>();
        tls.put("server_name", "sni.example");
        tls.put("tls_cert", "-----BEGIN CERTIFICATE-----");
        tls.put("tls_key", "-----BEGIN PRIVATE KEY-----");
        tls.put("pinned_peer_cert_sha256", "abc123");
        tls.put("private_key", "reality-priv");
        tls.put("ech_key", "ech");

        ServerService.stripSubscribeTlsSecrets(tls);

        assertNull(tls.get("tls_cert"));
        assertNull(tls.get("tls_key"));
        assertNull(tls.get("private_key"));
        assertNull(tls.get("ech_key"));
        assertEquals("abc123", tls.get("pinned_peer_cert_sha256"));
        assertEquals("sni.example", tls.get("server_name"));
        assertFalse(tls.containsKey("tls_cert"));
    }
}
