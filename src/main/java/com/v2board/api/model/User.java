package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String password;
    private String uuid;
    private String token;
    private Integer groupId;
    private Long planId;
    private Long expiredAt;
    private Long u; // 已用上传流量（字节）
    private Long d; // 已用下载流量（字节）
    @com.fasterxml.jackson.annotation.JsonProperty("transfer_enable")
    private Long transferEnable; // 总流量（字节）
    private Integer deviceLimit; // 设备限制数量
    private Integer banned; // 是否封禁 0-否 1-是
    private Integer isAdmin; // 是否管理员 0-否 1-是
    private Long createdAt; // Unix 时间戳
    private Long updatedAt; // Unix 时间戳
    // 以下字段根据 PHP 模型常用字段补充，允许为 null
    private Long lastLoginAt;
    private Integer autoRenewal;
    private Integer remindExpire;
    private Integer remindTraffic;
    private Long balance;
    private Long commissionBalance;
    private Integer commissionType;
    private Integer commissionRate;
    private Integer discount;
    private Long telegramId;
    private Long inviteUserId;
    private Integer speedLimit;
    private Long t; // 最后流量时间戳
}
