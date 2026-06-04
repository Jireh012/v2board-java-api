package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;

@Data
@TableName(value = "v2_server_trojan", autoResultMap = true)
public class ServerTrojan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private String port;

    @TableField("server_port")
    private Integer serverPort;

    /** 流量倍率 */
    private String rate;

    private String network;

    @TableField("server_name")
    private String serverName;

    @TableField("allow_insecure")
    private Integer allowInsecure;
    
    @TableField("`show`")
    private Integer show;
    
    private Integer sort;
    private Long parentId;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> groupId;

    /**
     * 传输层配置(JSON)，对应 network_settings
     */
    @TableField("network_settings")
    private String networkSettings;

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

