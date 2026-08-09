package com.v2board.api.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(JobDispatcher.class);

    @Autowired
    private RedisJobQueue jobQueue;

    @Autowired
    private V2boardQueueProperties props;

    public void dispatch(String queue, String type, Map<String, Object> data) {
        if (!props.isEnabled()) {
            logger.warn("Queue disabled; drop job type={} queue={}", type, queue);
            return;
        }
        JobPayload payload = JobPayload.create(queue, type, data);
        jobQueue.dispatch(payload);
    }

    public void dispatchOrderHandle(String tradeNo) {
        Map<String, Object> data = new HashMap<>();
        data.put("trade_no", tradeNo);
        dispatch(JobQueues.ORDER_HANDLE, JobQueues.TYPE_ORDER_HANDLE, data);
    }

    public void dispatchTrafficFetch(double rate, Map<String, List<Long>> traffic) {
        Map<String, Object> data = new HashMap<>();
        data.put("rate", rate);
        data.put("data", traffic);
        dispatch(JobQueues.TRAFFIC_FETCH, JobQueues.TYPE_TRAFFIC_FETCH, data);
    }

    public void dispatchStatUser(double rate, Map<String, List<Long>> traffic) {
        Map<String, Object> data = new HashMap<>();
        data.put("rate", rate);
        data.put("data", traffic);
        dispatch(JobQueues.STAT, JobQueues.TYPE_STAT_USER, data);
    }

    public void dispatchStatServer(double rate, long serverId, String serverType, Map<String, List<Long>> traffic) {
        Map<String, Object> data = new HashMap<>();
        data.put("rate", rate);
        data.put("server_id", serverId);
        data.put("server_type", serverType);
        data.put("data", traffic);
        dispatch(JobQueues.STAT, JobQueues.TYPE_STAT_SERVER, data);
    }

    public void dispatchSendEmail(String email, String subject, String templateName, Map<String, Object> templateValues) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email != null ? email : "");
        data.put("subject", subject != null ? subject : "");
        data.put("template", templateName != null ? templateName : "");
        data.put("values", templateValues != null ? templateValues : Map.of());
        dispatch(JobQueues.SEND_EMAIL, JobQueues.TYPE_SEND_EMAIL, data);
    }

    public void dispatchSendTelegram(long chatId, String text) {
        Map<String, Object> data = new HashMap<>();
        data.put("chat_id", chatId);
        data.put("text", text != null ? text : "");
        dispatch(JobQueues.SEND_TELEGRAM, JobQueues.TYPE_SEND_TELEGRAM, data);
    }
}
