package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_giftcard")
public class Giftcard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    /**
     * 类型：1-余额，2-延长有效期（天），3-增加流量（GB），4-清空流量，5-指定套餐。
     */
    private Integer type;

    private Integer value;

    @TableField("plan_id")
    private Long planId;

    @TableField("limit_use")
    private Integer limitUse;

    @TableField("used_user_ids")
    private String usedUserIds;

    @TableField("started_at")
    private Long startedAt;

    @TableField("ended_at")
    private Long endedAt;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}

