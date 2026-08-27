package com.v2board.api.service.external;

import com.v2board.api.model.ExternalSubscribeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalSubscribeTrafficTest {

    @Test
    void header_usedEqualsTotal_isExhausted() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                "upload=100; download=900; total=1000; expire=0", List.of());
        assertEquals(100L, snap.upload());
        assertEquals(900L, snap.download());
        assertEquals(1000L, snap.total());
        assertTrue(snap.exhausted());
    }

    @Test
    void header_totalZero_isUnlimited() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                "upload=1; download=2; total=0; expire=0", List.of());
        assertFalse(snap.exhausted());
        assertEquals(0L, snap.total());
    }

    @Test
    void header_remainingQuota_notExhausted() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                "upload=1; download=2; total=1000; expire=1735689600", List.of());
        assertFalse(snap.exhausted());
        assertEquals(1735689600L, snap.expire());
    }

    @Test
    void expireAlone_doesNotExhaust() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                "expire=1", List.of());
        assertFalse(snap.exhausted());
        assertNull(snap.total());
    }

    @Test
    void missingTrafficInfo_doesNotExclude() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(null, List.of("HK-01"));
        assertFalse(snap.exhausted());
        assertNull(snap.upload());
    }

    @Test
    void infoNode_zeroGb_isExhausted() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                null, List.of("剩余流量：0 GB", "套餐到期：2026-12-01"));
        assertTrue(snap.exhausted());
    }

    @Test
    void infoNode_usedUpWords_isExhausted() {
        assertTrue(ExternalSubscribeTraffic.resolveFrom(null, List.of("剩余流量：已用完")).exhausted());
        assertEquals(0L, ExternalSubscribeTraffic.remainingBytesFromName("Traffic left: exhausted"));
    }

    @Test
    void infoNode_positiveRemaining_notExhausted() {
        long remain = Math.round(58.73 * 1073741824d);
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                null, List.of("剩余流量：58.73 GB"));
        assertFalse(snap.exhausted());
        assertEquals(remain, ExternalSubscribeTraffic.remainingBytesFromName("剩余流量：58.73 GB"));
        assertEquals(remain, snap.total());
        assertEquals(0L, snap.upload());
        assertEquals(0L, snap.download());
    }

    @Test
    void infoNode_negativeRemaining_isExhausted() {
        long negative = ExternalSubscribeTraffic.remainingBytesFromName("剩余流量：-1.5 GB");
        assertTrue(negative < 0);
        assertTrue(ExternalSubscribeTraffic.resolveFrom(null, List.of("剩余流量：-1.5 GB")).exhausted());
        assertTrue(ExternalSubscribeTraffic.resolveFrom(null, List.of("剩余流量：－10 MB")).exhausted());
        assertTrue(ExternalSubscribeTraffic.resolveFrom(null, List.of("剩余流量：负1GB")).exhausted());
        assertTrue(ExternalSubscribeTraffic.resolveFrom(null, List.of("Traffic left: -1 GB")).exhausted());
    }

    @Test
    void header_usedExceedsTotal_isExhausted() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                "upload=200; download=900; total=1000; expire=0", List.of());
        assertTrue(snap.exhausted());
    }

    @Test
    void headerUnlimited_butInfoZero_isExhausted() {
        ExternalSubscribeTraffic.Snapshot snap = ExternalSubscribeTraffic.resolveFrom(
                "upload=0; download=0; total=0; expire=0",
                List.of("剩余流量：0.00 GB"));
        assertTrue(snap.exhausted());
    }

    @Test
    void isDeliverable_skipsExhaustedFlag() {
        ExternalSubscribeSource ok = new ExternalSubscribeSource();
        ok.setId(1L);
        ok.setTrafficExhausted(0);
        ExternalSubscribeSource dead = new ExternalSubscribeSource();
        dead.setId(2L);
        dead.setTrafficExhausted(1);
        assertTrue(ExternalSubscribeTraffic.isDeliverable(ok));
        assertFalse(ExternalSubscribeTraffic.isDeliverable(dead));
        assertFalse(ExternalSubscribeTraffic.isDeliverable(null));
    }
}
