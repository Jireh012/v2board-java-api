package com.v2board.api.controller.admin.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.ServerShadowsocksMapper;
import com.v2board.api.model.ServerShadowsocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/server/shadowsocks")
public class ShadowsocksController {

    @Autowired
    private ServerShadowsocksMapper serverShadowsocksMapper;

    @GetMapping("/fetch")
    public ApiResponse<List<ServerShadowsocks>> fetch() {
        List<ServerShadowsocks> list = serverShadowsocksMapper.selectList(
                new LambdaQueryWrapper<ServerShadowsocks>().orderByAsc(ServerShadowsocks::getSort));
        return ApiResponse.success(list);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody ServerShadowsocks body) {
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() != null) {
            ServerShadowsocks server = serverShadowsocksMapper.selectById(body.getId());
            if (server == null) {
                throw new BusinessException(500, "服务器不存在");
            }
            body.setUpdatedAt(now);
            if (serverShadowsocksMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
            return ApiResponse.success(true);
        }
        body.setCreatedAt(now);
        body.setUpdatedAt(now);
        if (serverShadowsocksMapper.insert(body) <= 0) {
            throw new BusinessException(500, "创建失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ServerShadowsocks server = serverShadowsocksMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "节点ID不存在");
        }
        if (serverShadowsocksMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("id") Long id,
            @RequestParam("show") Integer show) {
        ServerShadowsocks server = serverShadowsocksMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "该服务器不存在");
        }
        server.setShow(show);
        server.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (serverShadowsocksMapper.updateById(server) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/copy")
    public ApiResponse<Boolean> copy(@RequestParam("id") Long id) {
        ServerShadowsocks server = serverShadowsocksMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "服务器不存在");
        }
        long now = System.currentTimeMillis() / 1000;
        server.setId(null);
        server.setShow(0);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);
        if (serverShadowsocksMapper.insert(server) <= 0) {
            throw new BusinessException(500, "复制失败");
        }
        return ApiResponse.success(true);
    }
}
