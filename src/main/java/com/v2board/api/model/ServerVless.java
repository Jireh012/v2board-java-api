package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;

@Data
@TableName(value = "v2_server_vless", autoResultMap = true)
public class ServerVless {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer port;        // int(11) 类型
    private Integer serverPort;
    private String network;
    private Integer tls;
    
    @TableField("tls_settings")
    private String tlsSettings;  // 数据库字段名是 tls_settings
    
    @TableField("network_settings")
    private String networkSettings;  // 数据库字段名是 network_settings
    
    private String flow;
    private String tags;
    private String rate;
    
    @TableField("`show`")
    private Integer show;
    
    private Integer sort;
    private Long parentId;
    
    @TableField(value = "group_id", typeHandler = JacksonTypeHandler.class)
    private List<Object> groupId;  // 使用 Object 类型，因为 JSON 可能解析为 String 或 Integer
    
    private Long createdAt;      // Unix 时间戳
    private Long updatedAt;       // Unix 时间戳
}

