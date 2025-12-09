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
    private String serverName;
    private Integer allowInsecure;
    
    @TableField("`show`")
    private Integer show;
    
    private Integer sort;
    private Long parentId;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> groupId;
    
    private Long createdAt;      // Unix 时间戳
    private Long updatedAt;       // Unix 时间戳
}

