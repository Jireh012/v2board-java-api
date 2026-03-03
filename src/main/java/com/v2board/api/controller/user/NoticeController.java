package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.NoticeMapper;
import com.v2board.api.model.Notice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 对齐 PHP V1\\User\\NoticeController::fetch
 * 路由：GET /api/v1/user/notice/fetch
 */
@RestController
@RequestMapping("/api/v1/user/notice")
public class NoticeController {

    @Autowired
    private NoticeMapper noticeMapper;

    @GetMapping("/fetch")
    public ApiResponse<Object> fetch(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "current", required = false, defaultValue = "1") long current,
            @RequestParam(value = "pageSize", required = false, defaultValue = "5") long pageSize
    ) {
        if (id != null) {
            Notice notice = noticeMapper.selectOne(
                    new LambdaQueryWrapper<Notice>()
                            .eq(Notice::getId, id)
                            .eq(Notice::getShow, 1)
            );
            if (notice == null) {
                throw new BusinessException(404, "Notice not found");
            }
            return ApiResponse.success(notice);
        }

        if (pageSize < 1) {
            pageSize = 1;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getShow, 1)
                .orderByDesc(Notice::getCreatedAt);

        Page<Notice> page = new Page<>(current, pageSize);
        Page<Notice> result = noticeMapper.selectPage(page, wrapper);

        Map<String, Object> resp = new HashMap<>();
        resp.put("data", result.getRecords());
        resp.put("total", result.getTotal());
        return ApiResponse.success(resp);
    }
}

