package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.List;

@Data
@TableName(value = "v2_server_tuic", autoResultMap = true)
public class ServerTuic {
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

    @TableField("congestion_control")
    private String congestionControl;

    @TableField("zero_rtt_handshake")
    private Integer zeroRttHandshake;

    @TableField("`show`")
    private Integer show;

    private Integer sort;
    private Long parentId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> groupId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> routeId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Long createdAt;
    private Long updatedAt;
}

