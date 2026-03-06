package com.v2board.api.payment.driver;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.PaymentDriver;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对齐 PHP App\Payments\CoinPayments
 * pay(): 构建表单 URL → redirect 到 coinpayments.net
 * notify(): HMAC-SHA512 验签 via Hmac header
 */
public class CoinPaymentsDriver implements PaymentDriver {

    @Override
    public Map<String, Object> pay(Map<String, Object> config, Map<String, Object> order) {
        String merchantId = (String) config.get("coinpayments_merchant_id");
        String ipnSecret = (String) config.get("coinpayments_ipn_secret");
        String currency = (String) config.get("coinpayments_currency");
        if (merchantId == null || ipnSecret == null) {
            throw new BusinessException(500, "CoinPayments configuration is invalid");
        }
        if (currency == null || currency.isEmpty()) currency = "USDT";

        String tradeNo = String.valueOf(order.get("trade_no"));
        Object totalAmountObj = order.get("total_amount");
        double totalAmountYuan = 0;
        if (totalAmountObj instanceof Number) {
            totalAmountYuan = ((Number) totalAmountObj).doubleValue() / 100.0;
        }
        String notifyUrl = String.valueOf(order.get("notify_url"));
        String returnUrl = String.valueOf(order.get("return_url"));

        // 构建跳转 URL
        StringBuilder query = new StringBuilder();
        query.append("cmd=_pay_simple");
        query.append("&merchant=").append(encode(merchantId));
        query.append("&item_name=").append(encode(tradeNo));
        query.append("&item_number=").append(encode(tradeNo));
        query.append("&currency=").append(encode("CNY"));
        query.append("&currency1=").append(encode("CNY"));
        query.append("&want_currency=").append(encode(currency));
        query.append("&amountf=").append(String.format("%.2f", totalAmountYuan));
        query.append("&ipn_url=").append(encode(notifyUrl));
        query.append("&success_url=").append(encode(returnUrl));
        query.append("&cancel_url=").append(encode(returnUrl));
        query.append("&custom=").append(encode(tradeNo));

        String payUrl = "https://www.coinpayments.net/index.php?" + query;

        Map<String, Object> result = new HashMap<>();
        result.put("type", 1); // redirect URL
        result.put("data", payUrl);
        return result;
    }

    @Override
    public Map<String, String> notify(Map<String, Object> config, HttpServletRequest request) {
        String ipnSecret = (String) config.get("coinpayments_ipn_secret");
        String merchantId = (String) config.get("coinpayments_merchant_id");
        if (ipnSecret == null || merchantId == null) return null;

        // 读取原始请求体
        String payload;
        try {
            StringBuilder sb = new StringBuilder();
            var reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            payload = sb.toString();
        } catch (Exception e) {
            return null;
        }

        // HMAC header
        String hmacHeader = request.getHeader("Hmac");
        if (hmacHeader == null || hmacHeader.isEmpty()) return null;

        // HMAC-SHA512 验签
        String computedHmac = hmacSha512Hex(ipnSecret, payload);
        if (!hmacHeader.equalsIgnoreCase(computedHmac)) {
            return null;
        }

        // 解析表单参数
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        // 验证 merchant
        String reqMerchant = params.get("merchant");
        if (!merchantId.equals(reqMerchant)) return null;

        // 检查状态: status >= 100 表示完成, status == 2 表示排队
        String statusStr = params.get("status");
        if (statusStr == null) return null;
        int status;
        try {
            status = Integer.parseInt(statusStr);
        } catch (NumberFormatException e) {
            return null;
        }
        if (status < 2 && status < 100) return null;
        if (status < 100 && status != 2) return null;

        // 提取订单号
        String custom = params.get("custom");
        String txnId = params.get("txn_id");
        if (custom == null || custom.isEmpty()) {
            custom = params.get("item_number");
        }
        if (custom == null) return null;

        Map<String, String> result = new HashMap<>();
        result.put("trade_no", custom);
        result.put("callback_no", txnId != null ? txnId : "");
        return result;
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String hmacSha512Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString((b & 0xff));
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
