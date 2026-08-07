package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_external_subscribe_node")
public class ExternalSubscribeNode {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_id")
    private Long sourceId;

    private String name;

    private String protocol;

    @TableField("share_uri")
    private String shareUri;

    @TableField("singbox_outbound")
    private String singboxOutbound;

    private String fingerprint;

    private Integer reachable;

    @TableField("last_check_at")
    private Long lastCheckAt;

    private Integer sort;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
