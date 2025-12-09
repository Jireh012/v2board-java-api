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
    private String uuid;
    private String token;
    private Integer groupId;
    private Long planId;
    private Long expiredAt;
    private Long u;              // 已用上传流量（字节）
    private Long d;              // 已用下载流量（字节）
    private Long transferEnable; // 总流量（字节）
    private Integer deviceLimit; // 设备限制数量
    private Integer banned;      // 是否封禁 0-否 1-是
    private Long createdAt;      // Unix 时间戳
    private Long updatedAt;       // Unix 时间戳
}

