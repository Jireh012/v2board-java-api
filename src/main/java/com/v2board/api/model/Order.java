package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("plan_id")
    private Long planId;

    /**
     * 订阅周期：month_price / quarter_price / year_price / deposit / reset_price 等
     */
    private String period;

    @TableField("trade_no")
    private String tradeNo;

    /**
     * 总金额（分）
     */
    @TableField("total_amount")
    private Long totalAmount;

    /**
     * 订单状态：0-待支付，1-已支付，2-已取消，3-已完成 等
     */
    private Integer status;

    @TableField("payment_id")
    private Long paymentId;

    @TableField("handling_amount")
    private Long handlingAmount;

    @TableField("balance_amount")
    private Long balanceAmount;

    @TableField("surplus_amount")
    private Long surplusAmount;

    @TableField("surplus_order_ids")
    private String surplusOrderIds;

    @TableField("coupon_id")
    private Long couponId;

    @TableField("callback_no")
    private String callbackNo;

    @TableField("invite_user_id")
    private Long inviteUserId;

    @TableField("commission_balance")
    private Long commissionBalance;

    @TableField("commission_status")
    private Integer commissionStatus;

    /**
     * 订单类型：1-新购 2-续费 3-升级 4-流量重置等
     */
    private Integer type;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}

