package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.ExternalSubscribeSourceMapper;
import com.v2board.api.model.ExternalSubscribeNode;
import com.v2board.api.model.ExternalSubscribeSource;
import com.v2board.api.service.external.ExternalNameFilter;
import com.v2board.api.service.external.ExternalSubscribeNodeService;
import com.v2board.api.service.external.ExternalSubscribeSyncService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/external-subscribe")
public class AdminExternalSubscribeController {

    private final ExternalSubscribeSourceMapper sourceMapper;
    private final ExternalSubscribeNodeService nodeService;
    private final ExternalSubscribeSyncService syncService;

    public AdminExternalSubscribeController(ExternalSubscribeSourceMapper sourceMapper,
                                            ExternalSubscribeNodeService nodeService,
                                            ExternalSubscribeSyncService syncService) {
        this.sourceMapper = sourceMapper;
        this.nodeService = nodeService;
        this.syncService = syncService;
    }

    @GetMapping("/fetch")
    public ApiResponse<List<Map<String, Object>>> fetch() {
        List<ExternalSubscribeSource> list = sourceMapper.selectList(
                new LambdaQueryWrapper<ExternalSubscribeSource>().orderByDesc(ExternalSubscribeSource::getId));
        List<Long> ids = list.stream().map(ExternalSubscribeSource::getId).collect(Collectors.toList());
        Map<Long, long[]> counts = nodeService.countBySourceIds(ids);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExternalSubscribeSource s : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", s.getId());
            row.put("name", s.getName());
            row.put("url", s.getUrl());
            row.put("enable", s.getEnable());
            row.put("remark", s.getRemark());
            row.put("name_filters", ExternalNameFilter.toApiList(s.getNameFilters()));
            row.put("last_sync_at", s.getLastSyncAt());
            row.put("last_sync_status", s.getLastSyncStatus());
            row.put("last_sync_message", s.getLastSyncMessage());
            row.put("created_at", s.getCreatedAt());
            row.put("updated_at", s.getUpdatedAt());
            long[] c = counts.getOrDefault(s.getId(), new long[]{0, 0});
            row.put("node_count", c[0]);
            row.put("reachable_count", c[1]);
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? String.valueOf(body.get("name")).trim() : "";
        String url = body.get("url") != null ? String.valueOf(body.get("url")).trim() : "";
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(500, "名称不能为空");
        }
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(500, "订阅地址不能为空");
        }
        long now = System.currentTimeMillis() / 1000;
        Long id = toLong(body.get("id"));
        Integer enable = toInt(body.get("enable"), 0);
        String remark = body.get("remark") != null ? String.valueOf(body.get("remark")) : null;
        List<ExternalNameFilter.Rule> filters = ExternalNameFilter.parseAndValidateStrict(body.get("name_filters"));
        String nameFiltersJson = ExternalNameFilter.toJson(filters);

        if (id != null) {
            ExternalSubscribeSource existing = sourceMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(500, "订阅源不存在");
            }
            existing.setName(name);
            existing.setUrl(url);
            existing.setEnable(enable);
            existing.setRemark(remark);
            existing.setNameFilters(nameFiltersJson);
            existing.setUpdatedAt(now);
            if (sourceMapper.updateById(existing) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        } else {
            ExternalSubscribeSource source = new ExternalSubscribeSource();
            source.setName(name);
            source.setUrl(url);
            source.setEnable(enable);
            source.setRemark(remark);
            source.setNameFilters(nameFiltersJson);
            source.setCreatedAt(now);
            source.setUpdatedAt(now);
            if (sourceMapper.insert(source) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ExternalSubscribeSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(500, "订阅源不存在");
        }
        nodeService.deleteBySourceId(id);
        if (sourceMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestBody Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        if (id == null) {
            throw new BusinessException(500, "参数错误");
        }
        ExternalSubscribeSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(500, "订阅源不存在");
        }
        if (body.containsKey("enable")) {
            source.setEnable(toInt(body.get("enable"), 0));
        } else {
            Integer enable = source.getEnable() != null ? source.getEnable() : 0;
            source.setEnable(enable == 1 ? 0 : 1);
        }
        source.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (sourceMapper.updateById(source) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/sync")
    public ApiResponse<Boolean> sync(@RequestParam("id") Long id) {
        // 后台执行，避免长时间探测导致 HTTP 超时
        new Thread(() -> {
            try {
                syncService.syncOne(id);
            } catch (Exception ignored) {
                // 状态写入 source.last_sync_*
            }
        }, "external-subscribe-sync-" + id).start();
        return ApiResponse.success(true);
    }

    @PostMapping("/sync-all")
    public ApiResponse<Boolean> syncAll() {
        new Thread(() -> {
            try {
                syncService.syncAll();
            } catch (Exception ignored) {
            }
        }, "external-subscribe-sync-all").start();
        return ApiResponse.success(true);
    }

    @GetMapping("/nodes")
    public ApiResponse<List<Map<String, Object>>> nodes(@RequestParam("source_id") Long sourceId) {
        List<ExternalSubscribeNode> nodes = nodeService.listBySourceId(sourceId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExternalSubscribeNode n : nodes) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", n.getId());
            row.put("source_id", n.getSourceId());
            row.put("name", n.getName());
            row.put("protocol", n.getProtocol());
            row.put("share_uri", n.getShareUri());
            row.put("reachable", n.getReachable());
            row.put("last_check_at", n.getLastCheckAt());
            row.put("sort", n.getSort());
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return Long.parseLong(s);
    }

    private static int toInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
