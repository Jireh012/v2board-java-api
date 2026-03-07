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

    @TableField("listen_ip")
    private String listenIp;

    @TableField("protocol")
    private String protocol;

    @TableField("network")
    private String network;

    private Integer tls;

    private String flow;

    private String encryption;

    @TableField("disable_sni")
    private Integer disableSni;

    @TableField("udp_relay_mode")
    private String udpRelayMode;

    @TableField("zero_rtt_handshake")
    private Integer zeroRttHandshake;

    @TableField("congestion_control")
    private String congestionControl;

    private String cipher;

    @TableField("up_mbps")
    private Integer upMbps;

    @TableField("down_mbps")
    private Integer downMbps;

    private String obfs;

    @TableField("obfs_password")
    private String obfsPassword;

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

