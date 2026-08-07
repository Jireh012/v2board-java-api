package com.v2board.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "v2board.external-subscribe")
public class ExternalSubscribeProperties {

    private String singBoxPath = "sing-box";
    private String probeUrl = "https://www.gstatic.com/generate_204";
    private long probeTimeoutMs = 8000;
    private int concurrency = 4;
    private String cron = "0 */30 * * * *";

    public String getSingBoxPath() {
        return singBoxPath;
    }

    public void setSingBoxPath(String singBoxPath) {
        this.singBoxPath = singBoxPath;
    }

    public String getProbeUrl() {
        return probeUrl;
    }

    public void setProbeUrl(String probeUrl) {
        this.probeUrl = probeUrl;
    }

    public long getProbeTimeoutMs() {
        return probeTimeoutMs;
    }

    public void setProbeTimeoutMs(long probeTimeoutMs) {
        this.probeTimeoutMs = probeTimeoutMs;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }
}
