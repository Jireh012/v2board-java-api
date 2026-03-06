package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_stat")
public class Stat {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("record_at")
    private Long recordAt;

    @TableField("record_type")
    private String recordType;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("order_total")
    private Long orderTotal;

    @TableField("paid_count")
    private Integer paidCount;

    @TableField("paid_total")
    private Long paidTotal;

    @TableField("commission_count")
    private Integer commissionCount;

    @TableField("commission_total")
    private Long commissionTotal;

    @TableField("register_count")
    private Integer registerCount;

    @TableField("invite_count")
    private Integer inviteCount;

    @TableField("transfer_used_total")
    private Long transferUsedTotal;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
