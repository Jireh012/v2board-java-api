package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_stat_user")
public class StatUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long u;

    private Long d;

    @TableField("record_at")
    private Long recordAt;

    @TableField("user_id")
    private Long userId;

    @TableField("server_rate")
    private Double serverRate;

    private Long createdAt;

    private Long updatedAt;
}

