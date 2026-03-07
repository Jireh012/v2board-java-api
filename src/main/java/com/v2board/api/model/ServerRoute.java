package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_server_route")
public class ServerRoute {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 备注
     */
    private String remarks;

    /**
     * 匹配规则（JSON 数组字符串）
     */
    @TableField("`match`")
    private String match;

    /**
     * 动作类型：block, block_ip, block_port, protocol, dns, route, route_ip, default_out
     */
    private String action;

    /**
     * 动作值
     */
    @TableField("action_value")
    private String actionValue;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
