package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_ticket_message")
public class TicketMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_id")
    private Long ticketId;

    @TableField("user_id")
    private Long userId;

    private String message;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;

    @TableField(exist = false)
    private Boolean isMe;
}

