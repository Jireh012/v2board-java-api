package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_external_subscribe_source")
public class ExternalSubscribeSource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String url;

    private Integer enable;

    private String remark;

    @TableField("last_sync_at")
    private Long lastSyncAt;

    @TableField("last_sync_status")
    private String lastSyncStatus;

    @TableField("last_sync_message")
    private String lastSyncMessage;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
