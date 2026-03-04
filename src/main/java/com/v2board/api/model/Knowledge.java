package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_knowledge")
public class Knowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;

    private String title;

    private String language;

    /**
     * 是否展示：1-显示，0-隐藏
     * 列名为 MySQL 保留字 show，需用 TableField 转义。
     */
    @TableField(value = "`show`")
    private Integer show;

    private Integer sort;

    /**
     * 正文内容（HTML）
     */
    private String body;

    private Long createdAt;

    private Long updatedAt;
}

