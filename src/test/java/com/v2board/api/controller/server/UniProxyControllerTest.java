package com.v2board.api.controller.server;

import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UniProxyControllerTest {

    @Test
    void classicPaths_notMappedOnController() {
        assertNull(UniProxyController.class.getAnnotation(RequestMapping.class));
        assertNull(V2ServerController.class.getAnnotation(RequestMapping.class));
    }

    @Test
    void rejectPlaintextIdentityParams_rejectsTokenAndNodeId() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("token", "abc");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> UniProxyController.rejectPlaintextIdentityParams(req));
        assertEquals("token is error", ex.getMessage());

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setParameter("e", "ok");
        req2.setParameter("node_id", "1");
        assertThrows(BusinessException.class, () -> UniProxyController.rejectPlaintextIdentityParams(req2));

        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.setParameter("e", "cipher");
        assertDoesNotThrow(() -> UniProxyController.rejectPlaintextIdentityParams(ok));
    }


    @Test
    void buildBaseConfig_includesIntervalsAndMinTraffic() {
        UniProxyController controller = new UniProxyController();
        Map<String, Object> serverConfig = Map.of(
                "server_push_interval", 30,
                "server_pull_interval", 45,
                "server_node_report_min_traffic", 10,
                "server_device_online_min_traffic", 5
        );

        Map<String, Object> base = controller.buildBaseConfig(serverConfig);

        assertEquals(30, base.get("push_interval"));
        assertEquals(45, base.get("pull_interval"));
        assertEquals(10, base.get("node_report_min_traffic"));
        assertEquals(5, base.get("device_online_min_traffic"));
    }

    @Test
    void buildBaseConfig_defaultsWhenMissing() {
        UniProxyController controller = new UniProxyController();
        Map<String, Object> base = controller.buildBaseConfig(Map.of());

        assertEquals(60, base.get("push_interval"));
        assertEquals(60, base.get("pull_interval"));
        assertEquals(0, base.get("node_report_min_traffic"));
        assertEquals(0, base.get("device_online_min_traffic"));
    }

    @Test
    void isValidConfiguredNodeToken_rejectsBlankOrShort() {
        assertFalse(UniProxyController.isValidConfiguredNodeToken(null));
        assertFalse(UniProxyController.isValidConfiguredNodeToken(""));
        assertFalse(UniProxyController.isValidConfiguredNodeToken("   "));
        assertFalse(UniProxyController.isValidConfiguredNodeToken("123456789012345")); // 15
        assertTrue(UniProxyController.isValidConfiguredNodeToken("1234567890123456")); // 16
    }

    @Test
    void buildUserEntry_includesV2nodeUserInfoFields() {
        UniProxyController controller = new UniProxyController();
        User user = new User();
        user.setId(42L);
        user.setUuid("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        user.setSpeedLimit(100);
        user.setDeviceLimit(3);
        user.setGroupId(1);

        Map<String, Object> entry = controller.buildUserEntry(user);

        assertEquals(42L, entry.get("id"));
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", entry.get("uuid"));
        assertEquals(100, entry.get("speed_limit"));
        assertEquals(3, entry.get("device_limit"));
        assertEquals(1, entry.get("group_id"));
    }

    @Test
    void buildUserEntry_omitsNullSpeedLimit() {
        UniProxyController controller = new UniProxyController();
        User user = new User();
        user.setId(1L);
        user.setUuid("uuid");
        user.setSpeedLimit(null);

        Map<String, Object> entry = controller.buildUserEntry(user);

        assertFalse(entry.containsKey("speed_limit"));
        assertEquals(1L, entry.get("id"));
        assertEquals("uuid", entry.get("uuid"));
    }
}
