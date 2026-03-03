package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 支付通道标识，例如 alipay, stripe 等
     */
    private String payment;

    private String icon;

    @TableField("handling_fee_fixed")
    private Long handlingFeeFixed;

    @TableField("handling_fee_percent")
    private Integer handlingFeePercent;

    /**
     * 是否启用：1-启用，0-停用
     */
    private Integer enable;

    private Integer sort;

    /**
     * 自定义通知域名
     */
    @TableField("notify_domain")
    private String notifyDomain;

    /**
     * 支付网关配置（JSON 字符串）
     */
    private String config;

    /**
     * 唯一标识，用于通知回调
     */
    private String uuid;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}

