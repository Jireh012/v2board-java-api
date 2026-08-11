package com.v2board.api.service.external;

import java.time.Duration;

/**
 * Resolved subscribe.external_sync_* settings for scheduling.
 */
public record ExternalSyncSettings(
        boolean enabled,
        String mode,
        int intervalValue,
        String intervalUnit,
        String cron
) {
    public static final String MODE_INTERVAL = "interval";
    public static final String MODE_CRON = "cron";
    public static final String UNIT_MINUTE = "minute";
    public static final String UNIT_HOUR = "hour";
    public static final String UNIT_DAY = "day";
    public static final String DEFAULT_CRON = "0 */30 * * * *";

    public Duration intervalDuration() {
        return switch (intervalUnit == null ? UNIT_MINUTE : intervalUnit) {
            case UNIT_HOUR -> Duration.ofHours(Math.max(1, intervalValue));
            case UNIT_DAY -> Duration.ofDays(Math.max(1, intervalValue));
            default -> Duration.ofMinutes(Math.max(1, intervalValue));
        };
    }

    public boolean isCronMode() {
        return MODE_CRON.equalsIgnoreCase(mode);
    }
}
