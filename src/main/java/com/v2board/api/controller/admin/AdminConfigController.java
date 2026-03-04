package com.v2board.api.controller.admin;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端系统配置，对齐 PHP /config/fetch、/config/save。
 */
@RestController
@RequestMapping("/api/v1/admin/config")
public class AdminConfigController {

    @Autowired
    private ConfigService configService;

    /**
     * GET /api/v1/admin/config/fetch?key=site
     * 不传 key 返回完整配置；传 key 返回 { key: { ... } }。
     */
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(@RequestParam(required = false) String key) {
        try {
            Map<String, Object> data = configService.fetch(key);
            return ApiResponse.success(data);
        } catch (Exception e) {
            throw new RuntimeException("获取配置失败", e);
        }
    }

    /**
     * POST /api/v1/admin/config/save
     * Body 与 fetch 返回结构一致（可只传要更新的分组，会与现有配置合并）。
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Map<String, Object> body) {
        try {
            configService.save(body);
            return ApiResponse.success(true);
        } catch (Exception e) {
            throw new RuntimeException("保存配置失败", e);
        }
    }
}
