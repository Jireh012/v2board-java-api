package com.v2board.api.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientControllerSingBoxVersionTest {

    @Test
    void parsesThreePartVersions() {
        assertTrue(ClientController.isSingBoxVersionAtLeast("1.12.0", 1, 12));
        assertTrue(ClientController.isSingBoxVersionAtLeast("1.12", 1, 12));
        assertTrue(ClientController.isSingBoxVersionAtLeast("1.13.1", 1, 12));
        assertTrue(ClientController.isSingBoxVersionAtLeast("2.0.0", 1, 12));
        assertFalse(ClientController.isSingBoxVersionAtLeast("1.10.0", 1, 12));
        assertFalse(ClientController.isSingBoxVersionAtLeast("1.11.7", 1, 12));
        assertFalse(ClientController.isSingBoxVersionAtLeast(null, 1, 12));
    }
}
