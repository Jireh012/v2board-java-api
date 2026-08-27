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

    /**
     * 0 = direct fetch; 1 = auto pick a reachable library node as HTTP pre-proxy.
     */
    @TableField("pre_proxy_enable")
    private Integer preProxyEnable;

    private String remark;

    /**
     * JSON array: [{pattern, replacement, regex}] — display-name filters applied on sync.
     */
    @TableField("name_filters")
    private String nameFilters;

    @TableField("last_sync_at")
    private Long lastSyncAt;

    @TableField("last_sync_status")
    private String lastSyncStatus;

    @TableField("last_sync_message")
    private String lastSyncMessage;

    /** Upstream used upload bytes from subscription-userinfo (nullable). */
    @TableField("traffic_upload")
    private Long trafficUpload;

    /** Upstream used download bytes from subscription-userinfo (nullable). */
    @TableField("traffic_download")
    private Long trafficDownload;

    /** Upstream plan total bytes; 0/null = unlimited or unknown. */
    @TableField("traffic_total")
    private Long trafficTotal;

    /** Upstream plan expire unix seconds (nullable). */
    @TableField("traffic_expire")
    private Long trafficExpire;

    /** 1 = quota used up; exclude this source from user subscribe / pre-proxy. */
    @TableField("traffic_exhausted")
    private Integer trafficExhausted;

    @TableField("created_at")
    private Long createdAt;

    @TableField("updated_at")
    private Long updatedAt;
}
