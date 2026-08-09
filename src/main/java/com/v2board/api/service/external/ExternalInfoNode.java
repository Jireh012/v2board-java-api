package com.v2board.api.service.external;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects upstream "info" pseudo-nodes (traffic / expire banners) that are not real proxies.
 */
public final class ExternalInfoNode {

    /**
     * Names commonly injected by panels as fake proxies for display in clients.
     */
    private static final Pattern INFO_NAME = Pattern.compile(
            "(?i)("
                    + "剩余流量"
                    + "|套餐到期"
                    + "|距离下次重置"
                    + "|过期时间"
                    + "|到期时间"
                    + "|流量重置"
                    + "|重置剩余"
                    + "|expire\\s*time"
                    + "|traffic\\s*(left|remain)"
                    + "|package\\s*expire"
                    + "|剩余\\s*流量"
                    + ")"
    );

    private ExternalInfoNode() {
    }

    public static boolean isInfoName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim();
        // strip common security / emoji prefixes used in delivery
        n = n.replaceFirst("^[🔒⚠️\\s]+", "");
        return INFO_NAME.matcher(n).find();
    }

    public static boolean isInfoNode(CanonicalExternalNode node) {
        return node != null && isInfoName(node.getName());
    }

    /** Remove info pseudo-nodes in-place; returns removed count. */
    public static int removeFrom(List<CanonicalExternalNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int before = nodes.size();
        nodes.removeIf(ExternalInfoNode::isInfoNode);
        return before - nodes.size();
    }

}
