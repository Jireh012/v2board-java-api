package com.v2board.api.queue.handler;

import com.v2board.api.queue.JobHandler;
import com.v2board.api.queue.JobPayload;
import com.v2board.api.queue.JobQueues;
import com.v2board.api.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderHandleJobHandler implements JobHandler {

    @Autowired
    private OrderService orderService;

    @Override
    public String type() {
        return JobQueues.TYPE_ORDER_HANDLE;
    }

    @Override
    public void handle(JobPayload payload) {
        Object tradeNo = payload.getData().get("trade_no");
        if (tradeNo == null) {
            return;
        }
        orderService.handleOrder(String.valueOf(tradeNo));
    }
}
