package com.v2board.api.mapper;

/**
 * One user's traffic increments for {@link UserMapper#batchUpdateTraffic}.
 */
public record TrafficDelta(long userId, long upload, long download) {
}
