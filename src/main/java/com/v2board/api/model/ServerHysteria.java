package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;

@Data
@TableName(value = "v2_server_hysteria", autoResultMap = true)
public class ServerHysteria {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private String port;
    /** 流量倍率 */
    private String rate;

    /**
     * 协议版本，对应 version
     */
    private Integer version;

    /**
     * 监听端口，对应 server_port
     */
    @TableField("server_port")
    private Integer serverPort;

    /**
     * 证书域名，对应 server_name
     */
    @TableField("server_name")
    private String serverName;

    /**
     * 上下行带宽 Mbps
     */
    @TableField("up_mbps")
    private Integer upMbps;

    @TableField("down_mbps")
    private Integer downMbps;

    /**
     * 混淆相关字段
     */
    private String obfs;

    @TableField("obfs_password")
    private String obfsPassword;

    private Integer insecure;

    @TableField("`show`")
    private Integer show;
    
    private Integer sort;
    private Long parentId;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> groupId;

    /**
     * 路由 ID 列表，对应 route_id(JSON 数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> routeId;

    /**
     * 标签列表，对应 tags(JSON 数组)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    
    private Long createdAt;      // Unix 时间戳
    private Long updatedAt;       // Unix 时间戳
}

