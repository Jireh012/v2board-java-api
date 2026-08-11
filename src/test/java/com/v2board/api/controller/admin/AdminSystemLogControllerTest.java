package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.mapper.SystemLogMapper;
import com.v2board.api.model.SystemLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSystemLogControllerTest {

    private SystemLogMapper systemLogMapper;
    private AdminSystemController controller;

    @BeforeEach
    void setUp() {
        systemLogMapper = mock(SystemLogMapper.class);
        controller = new AdminSystemController();
        ReflectionTestUtils.setField(controller, "systemLogMapper", systemLogMapper);
    }

    @Test
    void getSystemLog_paginatesAndFiltersLevel() {
        SystemLog row = new SystemLog();
        row.setId(1L);
        row.setLevel("ERROR");
        row.setTitle("x");
        Page<SystemLog> page = new Page<>(2, 10);
        page.setRecords(List.of(row));
        page.setTotal(25);
        when(systemLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        ApiResponse<Map<String, Object>> resp = controller.getSystemLog(2, null, 10L, "ERROR");
        assertEquals(0, resp.getCode());
        Map<String, Object> data = resp.getData();
        assertNotNull(data);
        assertEquals(25L, data.get("total"));
        assertEquals(2L, data.get("current"));
        assertEquals(10L, data.get("pageSize"));
        assertEquals(1, ((List<?>) data.get("list")).size());

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(systemLogMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(10, pageCaptor.getValue().getSize());
    }

    @Test
    void getSystemLog_defaultsPageSize() {
        Page<SystemLog> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(systemLogMapper.selectPage(any(Page.class), any())).thenReturn(page);

        ApiResponse<Map<String, Object>> resp = controller.getSystemLog(1, null, null, null);
        assertEquals(20L, resp.getData().get("pageSize"));
    }
}
