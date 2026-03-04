package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.List;

@Data
@TableName(value = "v2_server_v2node", autoResultMap = true)
public class ServerV2node {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer port;

    /** 流量倍率 */
    private String rate;

    @TableField("server_port")
    private Integer serverPort;

    @TableField("server_name")
    private String serverName;

    @TableField("protocol")
    private String protocol;

    @TableField("network")
    private String network;

    @TableField(value = "group_id", typeHandler = JacksonTypeHandler.class)
    private List<Integer> groupId;

    @TableField(value = "route_id", typeHandler = JacksonTypeHandler.class)
    private List<Integer> routeId;

    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(value = "tls_settings")
    private String tlsSettings;

    @TableField(value = "network_settings")
    private String networkSettings;

    @TableField(value = "encryption_settings")
    private String encryptionSettings;

    @TableField(value = "padding_scheme", typeHandler = JacksonTypeHandler.class)
    private Object paddingScheme;

    @TableField("`show`")
    private Integer show;

    private Integer sort;
    private Long parentId;

    private Long createdAt;
    private Long updatedAt;
}

