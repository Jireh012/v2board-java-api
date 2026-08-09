package com.v2board.api.controller.admin.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminManageControllerTest {

    @Test
    void configString_treatsNullAndBlankAsEmpty() {
        assertEquals("", AdminManageController.configString(null));
        assertEquals("", AdminManageController.configString(""));
        assertEquals("", AdminManageController.configString("  "));
        assertEquals("", AdminManageController.configString("null"));
        assertEquals("https://panel.example", AdminManageController.configString(" https://panel.example "));
    }

    @Test
    void firstNonBlank_picksFirstUsable() {
        assertEquals("https://a", AdminManageController.firstNonBlank("", "null", "https://a", "https://b"));
        assertEquals("", AdminManageController.firstNonBlank("", null, "null"));
        assertEquals("https://origin", AdminManageController.firstNonBlank("", "", "https://origin"));
    }
}
