package com.v2board.api.service.external;

import java.util.Map;

/**
 * 解析后的第三方节点统一形态。
 */
public class CanonicalExternalNode {
    private String name;
    private String protocol;
    private String shareUri;
    private Map<String, Object> singboxOutbound;
    private String fingerprint;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getShareUri() {
        return shareUri;
    }

    public void setShareUri(String shareUri) {
        this.shareUri = shareUri;
    }

    public Map<String, Object> getSingboxOutbound() {
        return singboxOutbound;
    }

    public void setSingboxOutbound(Map<String, Object> singboxOutbound) {
        this.singboxOutbound = singboxOutbound;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
}
