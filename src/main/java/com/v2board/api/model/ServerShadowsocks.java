package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;

@Data
@TableName(value = "v2_server_shadowsocks", autoResultMap = true)
public class ServerShadowsocks {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private String port;
    private String cipher;
    /** 流量倍率，如 1、1.5、2 */
    private String rate;

    /**
     * 混淆类型，例如 http、none
     */
    private String obfs;

    /**
     * 混淆配置，对应 PHP 中的 obfs_settings（JSON）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object obfsSettings;
    
    @TableField("`show`")
    private Integer show;
    
    private Integer sort;
    private Long parentId;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> groupId;

    /**
     * 路由 ID 列表，对应 route_id（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> routeId;

    /**
     * 标签列表，对应 tags（JSON 数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    
    private Long createdAt;      // Unix 时间戳
    private Long updatedAt;       // Unix 时间戳
}

