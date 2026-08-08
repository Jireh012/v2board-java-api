package com.v2board.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramServiceEnableTest {

    @Test
    void parseEnableFlag_acceptsNumberBooleanString() {
        assertFalse(TelegramService.parseEnableFlag(null));
        assertFalse(TelegramService.parseEnableFlag(0));
        assertFalse(TelegramService.parseEnableFlag("0"));
        assertFalse(TelegramService.parseEnableFlag("false"));
        assertFalse(TelegramService.parseEnableFlag(""));
        assertFalse(TelegramService.parseEnableFlag(false));

        assertTrue(TelegramService.parseEnableFlag(1));
        assertTrue(TelegramService.parseEnableFlag("1"));
        assertTrue(TelegramService.parseEnableFlag(true));
        assertTrue(TelegramService.parseEnableFlag("true"));
        assertTrue(TelegramService.parseEnableFlag(2));
    }
}
