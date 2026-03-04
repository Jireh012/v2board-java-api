package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_stat_server")
public class StatServer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serverId;

    private String serverType;

    private Long u;

    private Long d;

    private String recordType;

    private Long recordAt;

    private Long createdAt;

    private Long updatedAt;
}

