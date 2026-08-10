package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_subscribe_rule_template")
public class SubscribeRuleTemplate {

    @TableId(value = "format", type = IdType.INPUT)
    private String format;

    private String content;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("update_source")
    private String updateSource;

    @TableField("updated_at")
    private Long updatedAt;

    @TableField("created_at")
    private Long createdAt;
}
