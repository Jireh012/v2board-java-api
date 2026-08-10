package com.v2board.api.controller.admin;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.service.RuleTemplateService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/subscribe-rule")
public class AdminSubscribeRuleController {

    private final RuleTemplateService ruleTemplateService;

    public AdminSubscribeRuleController(RuleTemplateService ruleTemplateService) {
        this.ruleTemplateService = ruleTemplateService;
    }

    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(@RequestParam("format") String format) {
        return ApiResponse.success(ruleTemplateService.fetch(format));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        String format = body.get("format") != null ? String.valueOf(body.get("format")) : null;
        String content = body.get("content") != null ? String.valueOf(body.get("content")) : null;
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(500, "content 不能为空");
        }
        String sourceUrl = body.get("source_url") != null ? String.valueOf(body.get("source_url")) : null;
        return ApiResponse.success(ruleTemplateService.save(format, content, sourceUrl, "manual"));
    }

    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync(@RequestBody Map<String, Object> body) {
        String format = body.get("format") != null ? String.valueOf(body.get("format")) : null;
        String url = body.get("url") != null ? String.valueOf(body.get("url"))
                : (body.get("source_url") != null ? String.valueOf(body.get("source_url")) : null);
        return ApiResponse.success(ruleTemplateService.sync(format, url));
    }

    @PostMapping("/restore")
    public ApiResponse<Map<String, Object>> restore(@RequestBody Map<String, Object> body) {
        String format = body.get("format") != null ? String.valueOf(body.get("format")) : null;
        return ApiResponse.success(ruleTemplateService.restore(format));
    }
}
