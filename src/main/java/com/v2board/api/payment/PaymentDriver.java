package com.v2board.api.payment;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 支付驱动接口。对齐 PHP App\Payments 各驱动的 pay() / notify() 方法。
 */
public interface PaymentDriver {

    /**
     * 发起支付。
     *
     * @param config  支付网关配置（从 Payment.config JSON 解析）
     * @param order   订单信息，包含 trade_no, total_amount, notify_url, return_url, user_id, stripe_token 等
     * @return 支付结果：{type: 0/1/2, data: ...}
     *         type=0 → data 为二维码内容/URL
     *         type=1 → data 为跳转 URL
     *         type=2 → data 为直接扣款结果
     */
    Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order);

    /**
     * 验证回调签名并返回订单信息。
     *
     * @param config  支付网关配置
     * @param request HTTP 回调请求
     * @return 验签成功返回 {trade_no, callback_no}，验签失败返回 null
     */
    Map<String, String> notify(Map<String, Object> config, HttpServletRequest request);
}
