package com.v2board.api.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("v2_plan")
public class Plan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer groupId;
    private Long transferEnable;
    private String name;
    private Integer speedLimit;
    
    @TableField("`show`")
    private Integer show;  // MySQL 保留关键字，需要转义
    
    private Integer sort;
    private Integer renew;
    private String content;
    private Integer monthPrice;
    private Integer quarterPrice;
    private Integer halfYearPrice;
    private Integer yearPrice;
    private Integer twoYearPrice;
    private Integer threeYearPrice;
    private Integer onetimePrice;
    private Integer resetPrice;
    private Integer resetTrafficMethod;  // 流量重置方式
    private Integer capacityLimit;
    private Long createdAt;
    private Long updatedAt;
}

