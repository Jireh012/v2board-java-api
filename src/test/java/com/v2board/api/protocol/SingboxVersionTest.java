package com.v2board.api.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingboxVersionTest {

    @Test
    void wantsJson_singAndSfaFamily() {
        assertTrue(SingboxVersion.wantsJson("sing-box"));
        assertTrue(SingboxVersion.wantsJson("flag=singbox"));
        assertTrue(SingboxVersion.wantsJson("SFA/1.14.0 (unknown; android 14)"));
        assertTrue(SingboxVersion.wantsJson("SFM/1.12.0"));
        assertFalse(SingboxVersion.wantsJson("v2rayN/7.24.9"));
        assertFalse(SingboxVersion.wantsJson("clash-meta"));
        assertFalse(SingboxVersion.wantsJson("sfa-without-slash"));
    }

    @Test
    void extract_spaceSlashAndOfficialApps() {
        assertEquals("1.14.0", SingboxVersion.extract("sing-box 1.14.0"));
        assertEquals("1.14.0", SingboxVersion.extract("sing-box/1.14.0"));
        assertEquals("1.14.0", SingboxVersion.extract("SFA/1.14.0 (unknown; android 14)"));
        assertEquals("1.12.0", SingboxVersion.extract("sfi/1.12.0"));
        assertNull(SingboxVersion.extract("sing-box"));
        assertNull(SingboxVersion.extract("v2rayN/7.24.9"));
    }

    @Test
    void extract_prefersSingBoxTokenOverSfaWhenBothPresent() {
        assertEquals("1.14.0", SingboxVersion.extract("SFA/1.11.0 sing-box 1.14.0"));
    }

    @Test
    void extract_mergesQueryFlagWithUserAgent() {
        assertEquals("1.14.0", SingboxVersion.extract("sing-box SFA/1.14.0"));
        assertNull(SingboxVersion.extract("sing-box v2rayN/7.24.9"));
    }

    @Test
    void templateAndDnsGates() {
        assertTrue(SingboxVersion.useLegacyTemplate("1.11.7"));
        assertFalse(SingboxVersion.useLegacyTemplate("1.12.0"));
        assertFalse(SingboxVersion.useLegacyTemplate("1.14.0"));
        assertFalse(SingboxVersion.useLegacyTemplate(null));

        assertFalse(SingboxVersion.needsDnsResponseMatch(null));
        assertFalse(SingboxVersion.needsDnsResponseMatch("1.13.1"));
        assertTrue(SingboxVersion.needsDnsResponseMatch("1.14.0"));
        assertTrue(SingboxVersion.needsDnsResponseMatch("1.14"));
        assertTrue(SingboxVersion.needsDnsResponseMatch("2.0.0"));
    }

    @Test
    void atLeast_threePartVersions() {
        assertTrue(SingboxVersion.atLeast("1.12.0", 1, 12));
        assertTrue(SingboxVersion.atLeast("1.12", 1, 12));
        assertTrue(SingboxVersion.atLeast("1.13.1", 1, 12));
        assertTrue(SingboxVersion.atLeast("2.0.0", 1, 12));
        assertFalse(SingboxVersion.atLeast("1.10.0", 1, 12));
        assertFalse(SingboxVersion.atLeast("1.11.7", 1, 12));
        assertFalse(SingboxVersion.atLeast(null, 1, 12));
    }
}
