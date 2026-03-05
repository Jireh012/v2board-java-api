package com.v2board.api.controller.admin.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.ServerVmessMapper;
import com.v2board.api.model.ServerVmess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/server/vmess")
public class VmessController {

    @Autowired
    private ServerVmessMapper serverVmessMapper;

    @GetMapping("/fetch")
    public ApiResponse<List<ServerVmess>> fetch() {
        List<ServerVmess> list = serverVmessMapper.selectList(
                new LambdaQueryWrapper<ServerVmess>().orderByAsc(ServerVmess::getSort));
        return ApiResponse.success(list);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody ServerVmess body) {
        long now = System.currentTimeMillis() / 1000;
        if (body.getId() != null) {
            ServerVmess server = serverVmessMapper.selectById(body.getId());
            if (server == null) {
                throw new BusinessException(500, "服务器不存在");
            }
            body.setUpdatedAt(now);
            if (serverVmessMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
            return ApiResponse.success(true);
        }
        body.setCreatedAt(now);
        body.setUpdatedAt(now);
        if (serverVmessMapper.insert(body) <= 0) {
            throw new BusinessException(500, "创建失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ServerVmess server = serverVmessMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "节点ID不存在");
        }
        if (serverVmessMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("id") Long id,
            @RequestParam("show") Integer show) {
        ServerVmess server = serverVmessMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "该服务器不存在");
        }
        server.setShow(show);
        server.setUpdatedAt(System.currentTimeMillis() / 1000);
        if (serverVmessMapper.updateById(server) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/copy")
    public ApiResponse<Boolean> copy(@RequestParam("id") Long id) {
        ServerVmess server = serverVmessMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "服务器不存在");
        }
        long now = System.currentTimeMillis() / 1000;
        server.setId(null);
        server.setShow(0);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);
        if (serverVmessMapper.insert(server) <= 0) {
            throw new BusinessException(500, "复制失败");
        }
        return ApiResponse.success(true);
    }
}
