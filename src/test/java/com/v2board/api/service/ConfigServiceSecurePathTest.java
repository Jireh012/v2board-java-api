package com.v2board.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceSecurePathTest {

    @Test
    void isValidSecurePath_rules() {
        assertTrue(ConfigService.isValidSecurePath("admin888"));
        assertTrue(ConfigService.isValidSecurePath("Ab12Cd34"));
        assertFalse(ConfigService.isValidSecurePath(""));
        assertFalse(ConfigService.isValidSecurePath("admin12")); // 7 chars
        assertFalse(ConfigService.isValidSecurePath("admin-888"));
        assertFalse(ConfigService.isValidSecurePath("dashboard")); // reserved exact match
        assertTrue(ConfigService.isValidSecurePath("login1234")); // prefix of reserved is ok
        assertFalse(ConfigService.isValidSecurePath("register")); // too short + reserved
    }
}
