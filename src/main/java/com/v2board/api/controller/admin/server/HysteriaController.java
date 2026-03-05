package com.v2board.api.controller.admin.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.v2board.api.mapper.ServerHysteriaMapper;
import com.v2board.api.model.ServerHysteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/server/hysteria")
public class HysteriaController {

    @Autowired
    private ServerHysteriaMapper serverHysteriaMapper;

    @GetMapping("/fetch")
    public ApiResponse<List<ServerHysteria>> fetch() {
        List<ServerHysteria> list = serverHysteriaMapper.selectList(
                new LambdaQueryWrapper<ServerHysteria>().orderByAsc(ServerHysteria::getSort));
        return ApiResponse.success(list);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody ServerHysteria body) {
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() != null) {
            ServerHysteria server = serverHysteriaMapper.selectById(body.getId());
            if (server == null) {
                throw new BusinessException(500, "服务器不存在");
            }
            body.setUpdatedAt(now);
            if (serverHysteriaMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
            return ApiResponse.success(true);
        }
        body.setCreatedAt(now);
        body.setUpdatedAt(now);
        if (serverHysteriaMapper.insert(body) <= 0) {
            throw new BusinessException(500, "创建失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ServerHysteria server = serverHysteriaMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "节点ID不存在");
        }
        if (serverHysteriaMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("id") Long id,
            @RequestParam("show") Integer show) {
        ServerHysteria server = serverHysteriaMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "该服务器不存在");
        }
        server.setShow(show);
        server.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (serverHysteriaMapper.updateById(server) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/copy")
    public ApiResponse<Boolean> copy(@RequestParam("id") Long id) {
        ServerHysteria server = serverHysteriaMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "服务器不存在");
        }
        long now = System.currentTimeMillis() / 1000;
        server.setId(null);
        server.setShow(0);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);
        if (serverHysteriaMapper.insert(server) <= 0) {
            throw new BusinessException(500, "复制失败");
        }
        return ApiResponse.success(true);
    }
}
