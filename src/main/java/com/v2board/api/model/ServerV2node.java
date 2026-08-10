package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@TableName(value = "v2_server_v2node", autoResultMap = true)
public class ServerV2node {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;

    /** 面板端口，可为范围字符串如 1000-2000 */
    private String port;

    /** 流量倍率 */
    private String rate;

    @TableField("server_port")
    private Integer serverPort;

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

    /** JSON 数组元素可能是 String 或 Integer（与 ServerVless / PHP 写入一致） */
    @TableField(value = "group_id", typeHandler = JacksonTypeHandler.class)
    private List<Object> groupId;

    @TableField(value = "route_id", typeHandler = JacksonTypeHandler.class)
    private List<Integer> routeId;

    @TableField(value = "tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(value = "tls_settings")
    private String tlsSettings;

    @TableField(value = "network_settings")
    private String networkSettings;

    /**
     * 信任的 X-Forwarded-For 头（JSON 数组），对齐 PHP {@code trusted_x_forwarded_for}。
     * <p>Must pin wire name: Jackson SNAKE_CASE maps {@code trustedXForwardedFor}
     * → {@code trusted_xforwarded_for} (XF treated as acronym), which breaks admin save.
     */
    @JsonProperty("trusted_x_forwarded_for")
    @JsonAlias({"trusted_xforwarded_for"})
    @TableField(value = "trusted_x_forwarded_for", typeHandler = JacksonTypeHandler.class)
    private List<String> trustedXForwardedFor;

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

