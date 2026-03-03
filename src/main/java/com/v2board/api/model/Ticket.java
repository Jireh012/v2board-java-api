package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("v2_ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String subject;

    /**
     * 工单等级：1-低 2-中 3-高
     */
    private Integer level;

    /**
     * 状态：0-打开 1-关闭
     */
    private Integer status;

    /**
     * 回复状态：0-用户最新回复 1-管理员最新回复
     */
    @TableField("reply_status")
    private Integer replyStatus;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    @TableField(exist = false)
    private List<TicketMessage> message;
}

