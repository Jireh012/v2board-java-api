package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_log")
public class SystemLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String level;

    private String host;

    private String uri;

    private String method;

    private String data;

    private String ip;

    private String context;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
