package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_commission_log")
public class CommissionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("invite_user_id")
    private Long inviteUserId;

    @TableField("trade_no")
    private String tradeNo;

    @TableField("order_amount")
    private Long orderAmount;

    @TableField("get_amount")
    private Long getAmount;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}

