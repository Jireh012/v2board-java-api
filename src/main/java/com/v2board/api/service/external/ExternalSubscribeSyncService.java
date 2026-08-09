package com.v2board.api.service.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.ExternalSubscribeNodeMapper;
import com.v2board.api.mapper.ExternalSubscribeSourceMapper;
import com.v2board.api.model.ExternalSubscribeNode;
import com.v2board.api.model.ExternalSubscribeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExternalSubscribeSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSubscribeSyncService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExternalSubscribeSourceMapper sourceMapper;
    private final ExternalSubscribeNodeMapper nodeMapper;
    private final ExternalSubscribeFetcher fetcher;
    private final ExternalSubscribeParser parser;
    private final SingBoxProbeService probeService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ExternalSubscribeSyncService(ExternalSubscribeSourceMapper sourceMapper,
                                        ExternalSubscribeNodeMapper nodeMapper,
                                        ExternalSubscribeFetcher fetcher,
                                        ExternalSubscribeParser parser,
                                        SingBoxProbeService probeService) {
        this.sourceMapper = sourceMapper;
        this.nodeMapper = nodeMapper;
        this.fetcher = fetcher;
        this.parser = parser;
        this.probeService = probeService;
    }

    /**
     * 进程内同步任务在重启后必然中断，但 DB 可能仍残留 running。
     * 启动时清理这些僵尸状态，避免前端一直显示「同步中」。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        int n = recoverInterruptedSyncs("服务重启，同步中断");
        if (n > 0) {
            logger.warn("Recovered {} interrupted external-subscribe sync state(s) after startup", n);
        }
    }

    /**
     * 将 last_sync_status=running 的记录标记为 failed。
     * 单实例下拿到内存锁后，库中残留的 running 一定属于已死亡的任务。
     */
    public int recoverInterruptedSyncs(String message) {
        List<ExternalSubscribeSource> stuck = sourceMapper.selectList(
                new LambdaQueryWrapper<ExternalSubscribeSource>()
                        .eq(ExternalSubscribeSource::getLastSyncStatus, "running"));
        if (stuck.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis() / 1000;
        for (ExternalSubscribeSource source : stuck) {
            finish(source, "failed", truncate(message, 1000), now);
            logger.warn("Marked interrupted sync as failed: sourceId={}", source.getId());
        }
        return stuck.size();
    }

    public void syncAll() {
        if (!running.compareAndSet(false, true)) {
            logger.info("External subscribe sync already running, skip");
            return;
        }
        try {
            recoverInterruptedSyncs("上次同步异常中断");
            List<ExternalSubscribeSource> sources = sourceMapper.selectList(
                    new LambdaQueryWrapper<ExternalSubscribeSource>().eq(ExternalSubscribeSource::getEnable, 1));
            for (ExternalSubscribeSource source : sources) {
                syncSourceInternal(source);
            }
        } finally {
            running.set(false);
        }
    }

    public void syncOne(Long id) {
        ExternalSubscribeSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(500, "订阅源不存在");
        }
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(500, "同步任务正在进行中，请稍后再试");
        }
        try {
            recoverInterruptedSyncs("上次同步异常中断");
            source = sourceMapper.selectById(id);
            if (source == null) {
                throw new BusinessException(500, "订阅源不存在");
            }
            syncSourceInternal(source);
        } finally {
            running.set(false);
        }
    }

    private void syncSourceInternal(ExternalSubscribeSource source) {
        long now = System.currentTimeMillis() / 1000;
        source.setLastSyncAt(now);
        source.setLastSyncStatus("running");
        source.setLastSyncMessage("同步中");
        source.setUpdatedAt(now);
        sourceMapper.updateById(source);

        try {
            if (!StringUtils.hasText(source.getUrl())) {
                throw new IllegalStateException("订阅地址为空");
            }
            if (!probeService.isSingBoxAvailable()) {
                throw new IllegalStateException("本机 sing-box 不可用，请配置 v2board.external-subscribe.sing-box-path");
            }

            String content = fetchSubscribeContent(source);
            List<CanonicalExternalNode> parsed = new ArrayList<>(parser.parse(content));
            ExternalNameFilter.applyFiltersToParsed(parsed, ExternalNameFilter.fromJson(source.getNameFilters()));
            int droppedInfo = ExternalInfoNode.removeFrom(parsed);
            if (droppedInfo > 0) {
                logger.info("Dropped {} info pseudo-node(s) from source {}", droppedInfo, source.getId());
            }
            if (parsed.isEmpty()) {
                // 清空旧节点
                nodeMapper.delete(new LambdaQueryWrapper<ExternalSubscribeNode>()
                        .eq(ExternalSubscribeNode::getSourceId, source.getId()));
                finish(source, "success", "未解析到节点", now);
                return;
            }

            Map<String, Boolean> probeResult = probeService.probeAll(parsed);
            Set<String> seen = new HashSet<>();
            int reachableCount = 0;
            int sort = 0;
            for (CanonicalExternalNode canonical : parsed) {
                seen.add(canonical.getFingerprint());
                boolean ok = Boolean.TRUE.equals(probeResult.get(canonical.getFingerprint()));
                if (ok) {
                    reachableCount++;
                }
                upsertNode(source.getId(), canonical, ok, now, sort++);
            }

            // 删除本次未出现的节点
            List<ExternalSubscribeNode> existing = nodeMapper.selectList(
                    new LambdaQueryWrapper<ExternalSubscribeNode>()
                            .eq(ExternalSubscribeNode::getSourceId, source.getId()));
            for (ExternalSubscribeNode old : existing) {
                if (!seen.contains(old.getFingerprint())) {
                    nodeMapper.deleteById(old.getId());
                }
            }

            finish(source, "success",
                    "解析 " + parsed.size() + " 个，连通 " + reachableCount + " 个", now);
            logger.info("Synced external source {}: {}", source.getId(), source.getLastSyncMessage());
        } catch (Exception e) {
            logger.error("Sync external source {} failed", source.getId(), e);
            finish(source, "failed", truncate(e.getMessage(), 1000), now);
        }
    }

    /**
     * Direct fetch, or auto-pick a reachable node from another enabled source as HTTP pre-proxy.
     */
    String fetchSubscribeContent(ExternalSubscribeSource source) throws Exception {
        boolean preProxy = source.getPreProxyEnable() != null && source.getPreProxyEnable() == 1;
        if (!preProxy) {
            return fetcher.fetch(source.getUrl());
        }
        ExternalSubscribeNode proxyNode = pickPreProxyNode(source.getId());
        if (proxyNode == null) {
            throw new IllegalStateException("前置代理已开启但无可用节点");
        }
        if (!StringUtils.hasText(proxyNode.getSingboxOutbound())) {
            throw new IllegalStateException("前置代理节点缺少 sing-box outbound");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> outbound = MAPPER.readValue(proxyNode.getSingboxOutbound(), Map.class);
        logger.info("External source {} fetch via pre-proxy node id={} name={}",
                source.getId(), proxyNode.getId(), proxyNode.getName());
        try (SingBoxProbeService.LocalHttpProxySession session = probeService.openHttpProxy(outbound)) {
            return fetcher.fetch(source.getUrl(), session.proxy());
        }
    }

    /**
     * Stable pick: other enabled sources' reachable nodes, order by sort ASC, id ASC.
     */
    ExternalSubscribeNode pickPreProxyNode(Long excludeSourceId) {
        List<ExternalSubscribeSource> enabled = sourceMapper.selectList(
                new LambdaQueryWrapper<ExternalSubscribeSource>().eq(ExternalSubscribeSource::getEnable, 1));
        List<Long> sourceIds = new ArrayList<>();
        for (ExternalSubscribeSource s : enabled) {
            if (s.getId() != null && (excludeSourceId == null || !s.getId().equals(excludeSourceId))) {
                sourceIds.add(s.getId());
            }
        }
        if (sourceIds.isEmpty()) {
            return null;
        }
        return nodeMapper.selectOne(
                new LambdaQueryWrapper<ExternalSubscribeNode>()
                        .in(ExternalSubscribeNode::getSourceId, sourceIds)
                        .eq(ExternalSubscribeNode::getReachable, 1)
                        .isNotNull(ExternalSubscribeNode::getSingboxOutbound)
                        .ne(ExternalSubscribeNode::getSingboxOutbound, "")
                        .orderByAsc(ExternalSubscribeNode::getSort)
                        .orderByAsc(ExternalSubscribeNode::getId)
                        .last("LIMIT 1"));
    }

    private void upsertNode(Long sourceId, CanonicalExternalNode canonical, boolean reachable,
                            long now, int sort) throws Exception {
        ExternalSubscribeNode existing = nodeMapper.selectOne(new LambdaQueryWrapper<ExternalSubscribeNode>()
                .eq(ExternalSubscribeNode::getSourceId, sourceId)
                .eq(ExternalSubscribeNode::getFingerprint, canonical.getFingerprint())
                .last("LIMIT 1"));
        String outboundJson = MAPPER.writeValueAsString(canonical.getSingboxOutbound());
        if (existing == null) {
            ExternalSubscribeNode node = new ExternalSubscribeNode();
            node.setSourceId(sourceId);
            node.setName(canonical.getName());
            node.setProtocol(canonical.getProtocol());
            node.setShareUri(canonical.getShareUri());
            node.setSingboxOutbound(outboundJson);
            node.setFingerprint(canonical.getFingerprint());
            node.setReachable(reachable ? 1 : 0);
            node.setLastCheckAt(now);
            node.setSort(sort);
            node.setCreatedAt(now);
            node.setUpdatedAt(now);
            nodeMapper.insert(node);
        } else {
            existing.setName(canonical.getName());
            existing.setProtocol(canonical.getProtocol());
            existing.setShareUri(canonical.getShareUri());
            existing.setSingboxOutbound(outboundJson);
            existing.setReachable(reachable ? 1 : 0);
            existing.setLastCheckAt(now);
            existing.setSort(sort);
            existing.setUpdatedAt(now);
            nodeMapper.updateById(existing);
        }
    }

    private void finish(ExternalSubscribeSource source, String status, String message, long now) {
        source.setLastSyncAt(now);
        source.setLastSyncStatus(status);
        source.setLastSyncMessage(message);
        source.setUpdatedAt(System.currentTimeMillis() / 1000);
        sourceMapper.updateById(source);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "unknown error";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
