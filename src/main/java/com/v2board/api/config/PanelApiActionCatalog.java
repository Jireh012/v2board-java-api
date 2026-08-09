package com.v2board.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exhaustive classic relative paths for passport/user/admin (relative to {@code /api/v1/{zone}}).
 * Wire URLs use aliases from {@link PanelApiActionAliases}; keep this list in sync when adding endpoints.
 */
public final class PanelApiActionCatalog {

    private static final String[] SERVER_TYPES = {
            "vmess", "vless", "trojan", "shadowsocks", "hysteria", "tuic", "anytls", "v2node"
    };

    private PanelApiActionCatalog() {
    }

    public static List<ZonePath> all() {
        List<ZonePath> out = new ArrayList<>();
        addPassport(out);
        addUser(out);
        addAdmin(out);
        return Collections.unmodifiableList(out);
    }

    private static void addPassport(List<ZonePath> out) {
        String z = "passport";
        out.add(zp(z, "auth/login"));
        out.add(zp(z, "auth/register"));
        out.add(zp(z, "auth/forget"));
        out.add(zp(z, "auth/getQuickLoginUrl"));
        out.add(zp(z, "auth/token2Login"));
        out.add(zp(z, "comm/sendEmailVerify"));
        out.add(zp(z, "comm/pv"));
    }

    private static void addUser(List<ZonePath> out) {
        String z = "user";
        out.add(zp(z, "checkLogin"));
        out.add(zp(z, "login"));
        out.add(zp(z, "info"));
        out.add(zp(z, "changePassword"));
        out.add(zp(z, "getActiveSession"));
        out.add(zp(z, "removeActiveSession"));
        out.add(zp(z, "getStat"));
        out.add(zp(z, "update"));
        out.add(zp(z, "unbindTelegram"));
        out.add(zp(z, "resetSecurity"));
        out.add(zp(z, "redeemGiftcard"));
        out.add(zp(z, "transferCommission"));
        out.add(zp(z, "getQuickLoginUrl"));
        out.add(zp(z, "getSubscribe"));
        out.add(zp(z, "trafficLog"));
        out.add(zp(z, "stat/getTrafficLog"));
        out.add(zp(z, "order/fetch"));
        out.add(zp(z, "order/detail"));
        out.add(zp(z, "order/save"));
        out.add(zp(z, "order/checkout"));
        out.add(zp(z, "order/check"));
        out.add(zp(z, "order/paymentMethod"));
        out.add(zp(z, "order/getPaymentMethod"));
        out.add(zp(z, "order/cancel"));
        out.add(zp(z, "invite/save"));
        out.add(zp(z, "invite/details"));
        out.add(zp(z, "invite/fetch"));
        out.add(zp(z, "server/fetch"));
        out.add(zp(z, "knowledge/fetch"));
        out.add(zp(z, "notice/fetch"));
        out.add(zp(z, "ticket/fetch"));
        out.add(zp(z, "ticket/save"));
        out.add(zp(z, "ticket/reply"));
        out.add(zp(z, "ticket/close"));
        out.add(zp(z, "ticket/withdraw"));
        out.add(zp(z, "plan/fetch"));
    }

    private static void addAdmin(List<ZonePath> out) {
        String z = "admin";
        out.add(zp(z, "login"));
        out.add(zp(z, "config/fetch"));
        out.add(zp(z, "config/save"));
        out.add(zp(z, "config/testSendMail"));
        out.add(zp(z, "config/setTelegramWebhook"));
        out.add(zp(z, "user/fetch"));
        out.add(zp(z, "user/getUserInfoById"));
        out.add(zp(z, "user/update"));
        out.add(zp(z, "user/generate"));
        out.add(zp(z, "user/ban"));
        out.add(zp(z, "user/delUser"));
        out.add(zp(z, "user/getLoginLog"));
        out.add(zp(z, "user/resetSecret"));
        out.add(zp(z, "user/dumpCSV"));
        out.add(zp(z, "plan/fetch"));
        out.add(zp(z, "plan/save"));
        out.add(zp(z, "plan/drop"));
        out.add(zp(z, "plan/update"));
        out.add(zp(z, "plan/sort"));
        out.add(zp(z, "order/detail"));
        out.add(zp(z, "order/fetch"));
        out.add(zp(z, "order/paid"));
        out.add(zp(z, "order/cancel"));
        out.add(zp(z, "order/update"));
        out.add(zp(z, "order/assign"));
        out.add(zp(z, "ticket/fetch"));
        out.add(zp(z, "ticket/reply"));
        out.add(zp(z, "ticket/close"));
        out.add(zp(z, "stat/getOverride"));
        out.add(zp(z, "stat/getOrder"));
        out.add(zp(z, "stat/getServerLastRank"));
        out.add(zp(z, "stat/getServerTodayRank"));
        out.add(zp(z, "stat/getUserTodayRank"));
        out.add(zp(z, "stat/getUserLastRank"));
        out.add(zp(z, "stat/getStatUser"));
        out.add(zp(z, "notice/fetch"));
        out.add(zp(z, "notice/save"));
        out.add(zp(z, "notice/show"));
        out.add(zp(z, "notice/drop"));
        out.add(zp(z, "coupon/fetch"));
        out.add(zp(z, "coupon/generate"));
        out.add(zp(z, "coupon/show"));
        out.add(zp(z, "coupon/drop"));
        out.add(zp(z, "giftcard/fetch"));
        out.add(zp(z, "giftcard/generate"));
        out.add(zp(z, "giftcard/drop"));
        out.add(zp(z, "knowledge/fetch"));
        out.add(zp(z, "knowledge/category"));
        out.add(zp(z, "knowledge/save"));
        out.add(zp(z, "knowledge/show"));
        out.add(zp(z, "knowledge/sort"));
        out.add(zp(z, "knowledge/drop"));
        out.add(zp(z, "payment/methods"));
        out.add(zp(z, "payment/fetch"));
        out.add(zp(z, "payment/form"));
        out.add(zp(z, "payment/show"));
        out.add(zp(z, "payment/save"));
        out.add(zp(z, "payment/drop"));
        out.add(zp(z, "payment/sort"));
        out.add(zp(z, "system/getSystemStatus"));
        out.add(zp(z, "system/getQueueStats"));
        out.add(zp(z, "external-subscribe/fetch"));
        out.add(zp(z, "external-subscribe/save"));
        out.add(zp(z, "external-subscribe/drop"));
        out.add(zp(z, "external-subscribe/update"));
        out.add(zp(z, "external-subscribe/sync"));
        out.add(zp(z, "external-subscribe/sync-all"));
        out.add(zp(z, "external-subscribe/nodes"));
        out.add(zp(z, "server/manage/getNodes"));
        out.add(zp(z, "server/manage/sort"));
        out.add(zp(z, "server/group/fetch"));
        out.add(zp(z, "server/group/save"));
        out.add(zp(z, "server/group/drop"));
        out.add(zp(z, "server/route/fetch"));
        out.add(zp(z, "server/route/save"));
        out.add(zp(z, "server/route/drop"));
        for (String type : SERVER_TYPES) {
            out.add(zp(z, "server/" + type + "/fetch"));
            out.add(zp(z, "server/" + type + "/save"));
            out.add(zp(z, "server/" + type + "/drop"));
            out.add(zp(z, "server/" + type + "/update"));
            out.add(zp(z, "server/" + type + "/copy"));
        }
    }

    private static ZonePath zp(String zone, String classicRel) {
        return new ZonePath(zone, classicRel);
    }

    public record ZonePath(String zone, String classicRel) {
    }
}
