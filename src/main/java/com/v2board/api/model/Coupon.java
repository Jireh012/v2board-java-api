package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    /**
     * 1-金额（分），2-比例（百分比）
     */
    private Integer type;

    private Integer value;

    @TableField(value = "`show`")
    private Integer show;

    @TableField("limit_use")
    private Integer limitUse;

    @TableField("limit_use_with_user")
    private Integer limitUseWithUser;

    @TableField("limit_plan_ids")
    private String limitPlanIds;

    @TableField("limit_period")
    private String limitPeriod;

    @TableField("started_at")
    private Long startedAt;

    @TableField("ended_at")
    private Long endedAt;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
