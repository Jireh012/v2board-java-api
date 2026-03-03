package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /**
     * 是否展示：1-显示，0-隐藏
     * 使用保留字列名 `show`，需要通过 TableField 显式转义。
     */
    @TableField(value = "`show`")
    private Integer show;

    private String imgUrl;

    /**
     * 标签（逗号分隔字符串，与 PHP 模型保持一致）
     */
    private String tags;

    private Long createdAt;

    private Long updatedAt;
}


