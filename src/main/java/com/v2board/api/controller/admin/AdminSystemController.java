package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.JobFailedMapper;
import com.v2board.api.model.JobFailed;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobWorkerManager;
import com.v2board.api.queue.RedisJobQueue;
import com.v2board.api.queue.V2boardQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端系统 / 队列状态 — 对齐 PHP SystemController 队列监控语义（非 Horizon）。
 */
@RestController
@RequestMapping("/api/v1/admin/system")
public class AdminSystemController {

    @Autowired
    private RedisJobQueue jobQueue;

    @Autowired
    private JobWorkerManager workerManager;

    @Autowired
    private V2boardQueueProperties queueProperties;

    @Autowired
    private JobFailedMapper jobFailedMapper;

    @GetMapping("/getSystemStatus")
    public ApiResponse<Map<String, Object>> getSystemStatus() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schedule", true);
        data.put("queue_workers", jobQueue.isWorkersRunning());
        data.put("horizon", false);
        data.put("uptime", runtime.getUptime() / 1000);
        data.put("java_version", System.getProperty("java.version"));
        return ApiResponse.success(data);
    }

    @GetMapping("/getQueueStats")
    public ApiResponse<Map<String, Object>> getQueueStats() {
        long failed = jobQueue.totalFailed();
        long recent = 0L;
        for (String q : queueProperties.getQueues().keySet()) {
            recent += jobQueue.completedThisHour(q);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", jobQueue.isWorkersRunning());
        data.put("failedJobs", failed);
        data.put("jobsPerMinute", recent > 0 ? Math.max(1, recent / 60) : 0);
        data.put("recentJobs", recent);
        data.put("processes", queueProperties.getQueues().values().stream()
                .mapToInt(V2boardQueueProperties.QueueConfig::getConcurrency).sum());
        return ApiResponse.success(data);
    }

    @GetMapping("/getQueueWorkload")
    public ApiResponse<List<Map<String, Object>>> getQueueWorkload() {
        return ApiResponse.success(jobQueue.workloadSnapshot(workerManager.activeSnapshot()));
    }

    @GetMapping("/getFailedJobs")
    public ApiResponse<Map<String, Object>> getFailedJobs(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize) {
        long page = Math.max(1, current);
        long size = Math.min(100, Math.max(1, pageSize));
        Page<JobFailed> p = jobFailedMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<JobFailed>().orderByDesc(JobFailed::getFailedAt));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", p.getRecords());
        data.put("total", p.getTotal());
        data.put("current", page);
        data.put("pageSize", size);
        return ApiResponse.success(data);
    }

    @PostMapping("/retryFailedJob")
    public ApiResponse<Boolean> retryFailedJob(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") instanceof Number n ? n.longValue() : null;
        if (id == null) {
            throw new BusinessException(422, "id 无效");
        }
        JobFailed row = jobFailedMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "失败任务不存在");
        }
        try {
            JobPayload payload = jobQueue.parse(row.getPayload());
            payload.setAttempts(0);
            jobQueue.dispatch(payload);
            jobFailedMapper.deleteById(id);
            return ApiResponse.success(true);
        } catch (Exception e) {
            throw new BusinessException(500, "重试失败: " + e.getMessage());
        }
    }

    @PostMapping("/deleteFailedJob")
    public ApiResponse<Boolean> deleteFailedJob(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") instanceof Number n ? n.longValue() : null;
        if (id == null) {
            throw new BusinessException(422, "id 无效");
        }
        jobFailedMapper.deleteById(id);
        return ApiResponse.success(true);
    }

    @PostMapping("/clearFailedJobs")
    public ApiResponse<Boolean> clearFailedJobs() {
        jobFailedMapper.delete(new LambdaQueryWrapper<>());
        return ApiResponse.success(true);
    }
}
