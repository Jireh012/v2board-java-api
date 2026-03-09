package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.NoticeMapper;
import com.v2board.api.model.Notice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端公告管理，对齐 PHP Admin\NoticeController。
 */
@RestController
@RequestMapping("/api/v1/admin/notice")
public class AdminNoticeController {

    @Autowired
    private NoticeMapper noticeMapper;

    @GetMapping("/fetch")
    public ApiResponse<List<Notice>> fetch() {
        List<Notice> list = noticeMapper.selectList(
                new LambdaQueryWrapper<Notice>().orderByDesc(Notice::getId));
        return ApiResponse.success(list);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Notice body) {
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() == null) {
            if (body.getTitle() == null || body.getTitle().isEmpty()) {
                throw new BusinessException(500, "标题不能为空");
            }
            body.setCreatedAt(now);
            body.setUpdatedAt(now);
            if (noticeMapper.insert(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        } else {
            Notice existing = noticeMapper.selectById(body.getId());
            if (existing == null) {
                throw new BusinessException(500, "公告不存在");
            }
            body.setUpdatedAt(now);
            body.setCreatedAt(existing.getCreatedAt());
            if (noticeMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/show")
    public ApiResponse<Boolean> show(@RequestParam("id") Long id) {
        if (id == null) throw new BusinessException(500, "参数有误");
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) throw new BusinessException(500, "公告不存在");
        notice.setShow(notice.getShow() != null && notice.getShow() == 1 ? 0 : 1);
        if (noticeMapper.updateById(notice) <= 0) throw new BusinessException(500, "保存失败");
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        if (id == null) throw new BusinessException(500, "参数错误");
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) throw new BusinessException(500, "公告不存在");
        if (noticeMapper.deleteById(id) <= 0) throw new BusinessException(500, "删除失败");
        return ApiResponse.success(true);
    }
}
