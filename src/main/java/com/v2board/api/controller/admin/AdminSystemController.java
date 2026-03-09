package com.v2board.api.controller.admin;

import com.v2board.api.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端系统状态，对齐 PHP Admin\SystemController（简化版，无 Horizon 依赖）。
 */
@RestController
@RequestMapping("/api/v1/admin/system")
public class AdminSystemController {

    @GetMapping("/getSystemStatus")
    public ApiResponse<Map<String, Object>> getSystemStatus() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schedule", true);
        data.put("horizon", true);
        data.put("uptime", runtime.getUptime() / 1000);
        data.put("java_version", System.getProperty("java.version"));
        return ApiResponse.success(data);
    }

    @GetMapping("/getQueueStats")
    public ApiResponse<Map<String, Object>> getQueueStats() {
        // Java 版本不使用 Horizon，返回简化状态
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", true);
        data.put("failedJobs", 0);
        data.put("jobsPerMinute", 0);
        data.put("processes", Runtime.getRuntime().availableProcessors());
        data.put("recentJobs", 0);
        return ApiResponse.success(data);
    }
}
