package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_job_failed")
public class JobFailed {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uuid;

    private String queue;

    @TableField("job_type")
    private String jobType;

    private String payload;

    private String exception;

    @TableField("failed_at")
    private Long failedAt;
}
