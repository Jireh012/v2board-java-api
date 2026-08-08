package com.v2board.api.controller;

import com.v2board.api.controller.ClientController.SubscribeInfoKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientControllerSubscribeInfoTest {

    @Test
    void method0_withReset_includesExpireResetTrafficInAddOrder() {
        assertEquals(
                List.of(SubscribeInfoKind.EXPIRE, SubscribeInfoKind.RESET, SubscribeInfoKind.TRAFFIC),
                ClientController.resolveSubscribeInfoKinds(0, true)
        );
    }

    @Test
    void method0_withoutReset_skipsReset() {
        assertEquals(
                List.of(SubscribeInfoKind.EXPIRE, SubscribeInfoKind.TRAFFIC),
                ClientController.resolveSubscribeInfoKinds(0, false)
        );
    }

    @Test
    void method1_expireOnly() {
        assertEquals(
                List.of(SubscribeInfoKind.EXPIRE),
                ClientController.resolveSubscribeInfoKinds(1, true)
        );
    }

    @Test
    void method2_trafficOnly() {
        assertEquals(
                List.of(SubscribeInfoKind.TRAFFIC),
                ClientController.resolveSubscribeInfoKinds(2, true)
        );
    }

    @Test
    void unknownMethod_treatedAsFull() {
        assertEquals(
                List.of(SubscribeInfoKind.EXPIRE, SubscribeInfoKind.RESET, SubscribeInfoKind.TRAFFIC),
                ClientController.resolveSubscribeInfoKinds(99, true)
        );
    }
}
