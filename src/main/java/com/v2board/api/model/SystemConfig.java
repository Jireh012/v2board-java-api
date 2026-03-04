package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统配置存储，与 PHP 版 config/v2board.php 对应。
 * 使用单行 name='v2board'，value 为完整 JSON。
 */
@Data
@TableName("v2_system_config")
public class SystemConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String value;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    public static final String NAME_V2BOARD = "v2board";
}
