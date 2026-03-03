package com.v2board.api.controller.admin.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.ServerVlessMapper;
import com.v2board.api.model.ServerVless;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/server/vless")
public class VlessController {

    @Autowired
    private ServerVlessMapper serverVlessMapper;

    @GetMapping("/fetch")
    public ApiResponse<List<ServerVless>> fetch() {
        List<ServerVless> list = serverVlessMapper.selectList(
                new LambdaQueryWrapper<ServerVless>().orderByAsc(ServerVless::getSort)
        );
        return ApiResponse.success(list);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> save(@RequestBody ServerVless body) {
        if (body.getId() != null) {
            ServerVless server = serverVlessMapper.selectById(body.getId());
            if (server == null) {
                throw new BusinessException(500, "服务器不存在");
            }
            if (serverVlessMapper.updateById(body) <= 0) {
                throw new BusinessException(500, "保存失败");
            }
            return ApiResponse.success(true);
        }
        if (serverVlessMapper.insert(body) <= 0) {
            throw new BusinessException(500, "创建失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/drop")
    public ApiResponse<Boolean> drop(@RequestParam("id") Long id) {
        ServerVless server = serverVlessMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "节点ID不存在");
        }
        if (serverVlessMapper.deleteById(id) <= 0) {
            throw new BusinessException(500, "删除失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/update")
    public ApiResponse<Boolean> update(@RequestParam("id") Long id,
                                       @RequestParam("show") Integer show) {
        ServerVless server = serverVlessMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "该服务器不存在");
        }
        server.setShow(show);
        if (serverVlessMapper.updateById(server) <= 0) {
            throw new BusinessException(500, "保存失败");
        }
        return ApiResponse.success(true);
    }

    @PostMapping("/copy")
    public ApiResponse<Boolean> copy(@RequestParam("id") Long id) {
        ServerVless server = serverVlessMapper.selectById(id);
        if (server == null) {
            throw new BusinessException(500, "服务器不存在");
        }
        server.setId(null);
        server.setShow(0);
        if (serverVlessMapper.insert(server) <= 0) {
            throw new BusinessException(500, "复制失败");
        }
        return ApiResponse.success(true);
    }
}

