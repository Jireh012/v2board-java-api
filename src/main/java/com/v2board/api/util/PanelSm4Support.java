package com.v2board.api.util;

/**
 * Shared constants / helpers for panel (passport / user / admin + public config) SM4 obfuscation.
 */
public final class PanelSm4Support {

    /** Request attribute: zone is under panel SM4 (client rewrite or public config). */
    public static final String ATTR_PANEL_SM4 = "com.v2board.panelSm4";

    /** Request attribute: reject classic Authorization / auth_data on encrypted zones. */
    public static final String ATTR_REJECT_CLASSIC_AUTH = "com.v2board.panelSm4.rejectClassicAuth";

    /** Encrypted JWT header (compact SM4). */
    public static final String HEADER_X_A = "X-A";

    private PanelSm4Support() {
    }

    public static boolean isPanelSm4(jakarta.servlet.http.HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(ATTR_PANEL_SM4));
    }

    public static boolean rejectClassicAuth(jakarta.servlet.http.HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(ATTR_REJECT_CLASSIC_AUTH));
    }
}
