package com.v2board.api.queue;

import java.util.LinkedHashMap;
import java.util.Map;

/** PHP Horizon-aligned queue names + admin Chinese labels. */
public final class JobQueues {

    public static final String ORDER_HANDLE = "order_handle";
    public static final String TRAFFIC_FETCH = "traffic_fetch";
    public static final String STAT = "stat";
    public static final String SEND_EMAIL = "send_email";
    public static final String SEND_TELEGRAM = "send_telegram";

    public static final String TYPE_ORDER_HANDLE = "OrderHandle";
    public static final String TYPE_TRAFFIC_FETCH = "TrafficFetch";
    public static final String TYPE_STAT_USER = "StatUser";
    public static final String TYPE_STAT_SERVER = "StatServer";
    public static final String TYPE_SEND_EMAIL = "SendEmail";
    public static final String TYPE_SEND_TELEGRAM = "SendTelegram";

    private JobQueues() {
    }

    public static Map<String, String> displayNames() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ORDER_HANDLE, "订单队列");
        m.put(SEND_EMAIL, "邮件队列");
        m.put(SEND_TELEGRAM, "Telegram消息队列");
        m.put(STAT, "统计队列");
        m.put(TRAFFIC_FETCH, "流量消费队列");
        return m;
    }
}
