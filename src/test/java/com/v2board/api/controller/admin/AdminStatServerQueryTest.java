package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.StatServerMapper;
import com.v2board.api.model.StatServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminStatServerQueryTest {

    @Test
    void parseUtcDate_rejectsBlankAndInvalid() {
        BusinessException missing = assertThrows(BusinessException.class,
                () -> StatServerQuery.parseUtcDate("  ", "开始日期"));
        assertEquals("请选择开始日期", missing.getMessage());
        BusinessException bad = assertThrows(BusinessException.class,
                () -> StatServerQuery.parseUtcDate("2026/08/01", "开始日期"));
        assertEquals("开始日期格式不正确", bad.getMessage());
        assertEquals(LocalDate.of(2026, 8, 1), StatServerQuery.parseUtcDate("2026-08-01", "开始日期"));
    }

    @Test
    void validateRange_inclusiveMax62Days() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        StatServerQuery.validateRange(start, start.plusDays(61));
        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> StatServerQuery.validateRange(start, start.plusDays(62)));
        assertTrue(tooLong.getMessage().contains("62"));
        BusinessException inverted = assertThrows(BusinessException.class,
                () -> StatServerQuery.validateRange(start.plusDays(1), start));
        assertEquals("开始日期不能晚于结束日期", inverted.getMessage());
    }

    @Test
    void typesForQuery_vmessIncludesLegacyV2ray() {
        assertEquals(List.of("vmess", "v2ray"), StatServerQuery.typesForQuery("vmess"));
        assertEquals(List.of("vmess", "v2ray"), StatServerQuery.typesForQuery("v2ray"));
        assertEquals(List.of("v2node"), StatServerQuery.typesForQuery("v2node"));
    }

    @Test
    void utcStartEpoch_isUtcMidnight() {
        assertEquals(
                LocalDate.of(2026, 8, 28).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                StatServerQuery.utcStartEpoch(LocalDate.of(2026, 8, 28)));
    }

    @Test
    void assemble_emptyDays_totalsZero_bytesNotGb() {
        LocalDate start = LocalDate.of(2026, 7, 30);
        LocalDate end = LocalDate.of(2026, 8, 28);
        Map<String, Object> data = StatServerQuery.assemble(9L, "v2node", "香港 1", start, end, List.of());
        assertEquals(9L, data.get("server_id"));
        assertEquals("v2node", data.get("server_type"));
        assertEquals("香港 1", data.get("server_name"));
        assertEquals("2026-07-30", data.get("start_date"));
        assertEquals("2026-08-28", data.get("end_date"));
        assertEquals(0L, data.get("u"));
        assertEquals(0L, data.get("d"));
        assertEquals(0L, data.get("total"));
        assertEquals(List.of(), data.get("days"));
    }

    @Test
    void assemble_mergesSameDay_keepsBytes_sortsDesc() {
        long day27 = LocalDate.of(2026, 8, 27).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long day28 = LocalDate.of(2026, 8, 28).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        StatServer vmess = row(1L, "vmess", day28, 100L, 200L);
        StatServer v2ray = row(1L, "v2ray", day28, 10L, 20L);
        StatServer earlier = row(1L, "vmess", day27, 1L, 2L);

        Map<String, Object> data = StatServerQuery.assemble(
                1L, "vmess", "旧节点",
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 28),
                List.of(vmess, earlier, v2ray));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) data.get("days");
        assertEquals(2, days.size());
        assertEquals("2026-08-28", days.get(0).get("date"));
        assertEquals(110L, days.get(0).get("u"));
        assertEquals(220L, days.get(0).get("d"));
        assertEquals(330L, days.get(0).get("total"));
        assertEquals("2026-08-27", days.get(1).get("date"));
        assertEquals(1L, days.get(1).get("u"));
        assertEquals(2L, days.get(1).get("d"));
        assertEquals(111L, data.get("u"));
        assertEquals(222L, data.get("d"));
        assertEquals(333L, data.get("total"));
        assertTrue(data.get("total") instanceof Long);
    }

    @Test
    void controller_missingNode_throwsBusinessException() {
        AdminStatController controller = new AdminStatController();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.getStatServer(null, "v2node", "2026-08-01", "2026-08-02"));
        assertEquals("请选择节点", ex.getMessage());
    }

    @Test
    void controller_emptyResult_isSuccessWithZeroTotals() {
        StatServerMapper mapper = mock(StatServerMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        AdminStatController controller = new AdminStatController();
        ReflectionTestUtils.setField(controller, "statServerMapper", mapper);
        stubEmptyServerMappers(controller);

        ApiResponse<Map<String, Object>> resp = controller.getStatServer(
                3L, "v2node", "2026-08-01", "2026-08-02");
        assertEquals(0, resp.getCode());
        assertEquals(List.of(), resp.getData().get("days"));
        assertEquals(0L, resp.getData().get("total"));
        assertEquals("", resp.getData().get("server_name"));
    }

    private static StatServer row(long serverId, String type, long recordAt, long u, long d) {
        StatServer s = new StatServer();
        s.setServerId(serverId);
        s.setServerType(type);
        s.setRecordType("d");
        s.setRecordAt(recordAt);
        s.setU(u);
        s.setD(d);
        return s;
    }

    private static void stubEmptyServerMappers(AdminStatController controller) {
        ReflectionTestUtils.setField(controller, "serverShadowsocksMapper", emptyListMapper(com.v2board.api.mapper.ServerShadowsocksMapper.class));
        ReflectionTestUtils.setField(controller, "serverVmessMapper", emptyListMapper(com.v2board.api.mapper.ServerVmessMapper.class));
        ReflectionTestUtils.setField(controller, "serverVlessMapper", emptyListMapper(com.v2board.api.mapper.ServerVlessMapper.class));
        ReflectionTestUtils.setField(controller, "serverTrojanMapper", emptyListMapper(com.v2board.api.mapper.ServerTrojanMapper.class));
        ReflectionTestUtils.setField(controller, "serverHysteriaMapper", emptyListMapper(com.v2board.api.mapper.ServerHysteriaMapper.class));
        ReflectionTestUtils.setField(controller, "serverTuicMapper", emptyListMapper(com.v2board.api.mapper.ServerTuicMapper.class));
        ReflectionTestUtils.setField(controller, "serverAnytlsMapper", emptyListMapper(com.v2board.api.mapper.ServerAnytlsMapper.class));
        ReflectionTestUtils.setField(controller, "serverV2nodeMapper", emptyListMapper(com.v2board.api.mapper.ServerV2nodeMapper.class));
    }

    private static <T extends BaseMapper<?>> T emptyListMapper(Class<T> type) {
        T mapper = mock(type);
        when(mapper.selectList(any())).thenReturn(List.of());
        return mapper;
    }
}
