package com.v2board.api.payment;

import com.v2board.api.common.BusinessException;
import com.v2board.api.payment.driver.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付驱动工厂，按方法名获取对应驱动实例。
 */
@Component
public class PaymentDriverFactory {

    private final Map<String, PaymentDriver> drivers = new HashMap<>();

    public PaymentDriverFactory() {
        drivers.put("AlipayF2F", new AlipayF2FDriver());
        drivers.put("MGate", new MGateDriver());
        drivers.put("EPay", new EPayDriver());
        drivers.put("WechatPayNative", new WechatPayNativeDriver());
        drivers.put("StripeAlipay", new StripeAlipayDriver());
        drivers.put("StripeWepay", new StripeWepayDriver());
        drivers.put("StripeCredit", new StripeCreditDriver());
        drivers.put("StripeCheckout", new StripeCheckoutDriver());
        drivers.put("StripeALL", new StripeALLDriver());
        drivers.put("BTCPay", new BTCPayDriver());
        drivers.put("Coinbase", new CoinbaseDriver());
        drivers.put("CoinPayments", new CoinPaymentsDriver());
        drivers.put("BEasyPaymentUSDT", new BEasyPaymentUSDTDriver());
    }

    public PaymentDriver getDriver(String method) {
        PaymentDriver driver = drivers.get(method);
        if (driver == null) {
            throw new BusinessException(500, "Unsupported payment method: " + method);
        }
        return driver;
    }

    public boolean hasDriver(String method) {
        return drivers.containsKey(method);
    }
}
