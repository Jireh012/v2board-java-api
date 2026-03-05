package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.KnowledgeMapper;
import com.v2board.api.model.Knowledge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class AdminKnowledgeController {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    /**
     * 对齐 PHP Admin\\KnowledgeController::fetch
     */
    @GetMapping("/fetch")
    public ApiResponse<Object> fetch(@RequestParam(value = "id", required = false) Long id) {
        if (id != null) {
            Knowledge knowledge = knowledgeMapper.selectById(id);
            if (knowledge == null) {
                throw new BusinessException(500, "知识不存在");
            }
            return ApiResponse.success(knowledge);
        }
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Knowledge::getTitle, Knowledge::getId, Knowledge::getUpdatedAt,
                Knowledge::getCategory, Knowledge::getShow)
                .orderByAsc(Knowledge::getSort);
        List<Knowledge> list = knowledgeMapper.selectList(wrapper);
        return ApiResponse.success(list);
    }

    /**
     * 获取所有分类列表（键集合）
     */
    @GetMapping("/category")
    public ApiResponse<List<String>> getCategory() {
        List<Knowledge> all = knowledgeMapper.selectList(new LambdaQueryWrapper<>());
        Set<String> categories = all.stream()
                .map(Knowledge::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.toSet());
        return ApiResponse.success(new ArrayList<>(categories));
    }

    /**
     * 创建或更新知识库文章。
     * 若 body.id 为空则创建，否则更新。
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Knowledge body) {
        if (body.getId() == null) {
            long now = System.currentTimeMillis() / 1000;
            body.setCreatedAt(now);
            body.setUpdatedAt(now);
            int inserted = knowledgeMapper.insert(body);
            if (inserted <= 0) {
                throw new BusinessException(500, "创建失败");
            }
        } else {
            Knowledge exists = knowledgeMapper.selectById(body.getId());
            if (exists == null) {
                throw new BusinessException(500, "知识不存在");
            }
            body.setCreatedAt(exists.getCreatedAt());
            if (knowledgeMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        }
        return ApiResponse.success(true);
    }

    /**
     * 显隐切换。
     */
    @PostMapping("/show")
    public ApiResponse<Boolean> show(@RequestParam("id") Long id) {
        if (id == null) {
            throw new BusinessException(500, "参数有误");
        }
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BusinessException(500, "知识不存在");
        }
        Integer show = knowledge.getShow() != null ? knowledge.getShow() : 0;
        knowledge.setShow(show == 1 ? 0 : 1);
        if (knowledgeMapper.updateById(knowledge) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    public static class SortRequest {
        private List<Long> knowledgeIds;

        public List<Long> getKnowledgeIds() {
            return knowledgeIds;
        }

        public void setKnowledgeIds(List<Long> knowledgeIds) {
            this.knowledgeIds = knowledgeIds;
        }
    }

    /**
     * 排序。
     */
    @PostMapping("/sort")
    public ApiResponse<Boolean> sort(@RequestBody SortRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getKnowledgeIds())) {
            throw new BusinessException(500, "保存失败");
        }
        List<Long> ids = request.getKnowledgeIds();
        int sort = 1;
        for (Long id : ids) {
            Knowledge k = knowledgeMapper.selectById(id);
            if (k != null) {
                k.setSort(sort++);
                knowledgeMapper.updateById(k);
            }
        }
        return ApiResponse.success(true);
    }

    /**
     * 删除。
     */
    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        if (id == null) {
            throw new BusinessException(500, "参数有误");
        }
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BusinessException(500, "知识不存在");
        }
        if (knowledgeMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }
}
