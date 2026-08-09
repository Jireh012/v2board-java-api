package com.v2board.api.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.config.ExternalSubscribeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SingBoxProbeService {

    private static final Logger logger = LoggerFactory.getLogger(SingBoxProbeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger PORT_SEQ = new AtomicInteger(18000);

    private final ExternalSubscribeProperties properties;

    public SingBoxProbeService(ExternalSubscribeProperties properties) {
        this.properties = properties;
    }

    public boolean isSingBoxAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(properties.getSingBoxPath(), "version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            logger.warn("sing-box not available at {}: {}", properties.getSingBoxPath(), e.getMessage());
            return false;
        }
    }

    /**
     * 并发探测节点连通性，返回 fingerprint -> reachable。
     */
    public Map<String, Boolean> probeAll(List<CanonicalExternalNode> nodes) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (nodes == null || nodes.isEmpty()) {
            return result;
        }
        int concurrency = Math.max(1, properties.getConcurrency());
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<ProbeResult>> tasks = new ArrayList<>();
            for (CanonicalExternalNode node : nodes) {
                tasks.add(() -> new ProbeResult(node.getFingerprint(), probeOne(node.getSingboxOutbound())));
            }
            List<Future<ProbeResult>> futures = pool.invokeAll(tasks,
                    properties.getProbeTimeoutMs() * nodes.size() / concurrency + 60_000L,
                    TimeUnit.MILLISECONDS);
            for (Future<ProbeResult> f : futures) {
                try {
                    if (f.isDone() && !f.isCancelled()) {
                        ProbeResult pr = f.get();
                        result.put(pr.fingerprint, pr.ok);
                    }
                } catch (Exception e) {
                    logger.debug("probe future failed: {}", e.getMessage());
                }
            }
            for (CanonicalExternalNode node : nodes) {
                result.putIfAbsent(node.getFingerprint(), false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            for (CanonicalExternalNode node : nodes) {
                result.putIfAbsent(node.getFingerprint(), false);
            }
        } finally {
            pool.shutdownNow();
        }
        return result;
    }

    public boolean probeOne(Map<String, Object> outbound) {
        if (outbound == null || outbound.isEmpty()) {
            return false;
        }
        try (LocalHttpProxySession session = openHttpProxy(outbound)) {
            return httpThroughProxy(session.localPort());
        } catch (Exception e) {
            logger.debug("probe failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Start a short-lived local sing-box mixed inbound whose final outbound is {@code outbound}.
     * Caller must close the session (process + temp config).
     */
    public LocalHttpProxySession openHttpProxy(Map<String, Object> outbound) throws Exception {
        if (outbound == null || outbound.isEmpty()) {
            throw new IllegalArgumentException("前置代理 outbound 为空");
        }
        if (!isSingBoxAvailable()) {
            throw new IllegalStateException("本机 sing-box 不可用，请配置 v2board.external-subscribe.sing-box-path");
        }
        int localPort = nextLocalPort();
        Map<String, Object> config = buildProbeConfig(outbound, localPort);
        Path configFile = Files.createTempFile("singbox-preproxy-", ".json");
        Files.writeString(configFile, MAPPER.writeValueAsString(config), StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder(
                properties.getSingBoxPath(), "run", "-c", configFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            if (!waitPortOpen(localPort, Math.min(8000, Math.max(3000, properties.getProbeTimeoutMs())))) {
                destroyQuietly(process);
                Files.deleteIfExists(configFile);
                throw new IllegalStateException("前置代理本地端口未就绪");
            }
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", localPort));
            return new LocalHttpProxySession(process, configFile, proxy, localPort);
        } catch (Exception e) {
            destroyQuietly(process);
            try {
                Files.deleteIfExists(configFile);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    public static class LocalHttpProxySession implements AutoCloseable {
        private final Process process;
        private final Path configFile;
        private final Proxy proxy;
        private final int localPort;

        private LocalHttpProxySession(Process process, Path configFile, Proxy proxy, int localPort) {
            this.process = process;
            this.configFile = configFile;
            this.proxy = proxy;
            this.localPort = localPort;
        }

        public Proxy proxy() {
            return proxy;
        }

        public int localPort() {
            return localPort;
        }

        @Override
        public void close() {
            destroyQuietly(process);
            try {
                Files.deleteIfExists(configFile);
            } catch (IOException ignored) {
            }
        }
    }

    private static void destroyQuietly(Process process) {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private Map<String, Object> buildProbeConfig(Map<String, Object> outbound, int localPort) {
        Map<String, Object> node = new LinkedHashMap<>(outbound);
        node.put("tag", "probe-node");

        Map<String, Object> inbound = new LinkedHashMap<>();
        inbound.put("type", "mixed");
        inbound.put("tag", "mixed-in");
        inbound.put("listen", "127.0.0.1");
        inbound.put("listen_port", localPort);

        Map<String, Object> direct = new LinkedHashMap<>();
        direct.put("type", "direct");
        direct.put("tag", "direct");

        List<Map<String, Object>> outbounds = new ArrayList<>();
        outbounds.add(node);
        outbounds.add(direct);

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("final", "probe-node");

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("level", "panic");
        log.put("disabled", true);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("log", log);
        config.put("inbounds", List.of(inbound));
        config.put("outbounds", outbounds);
        config.put("route", route);
        return config;
    }

    private boolean httpThroughProxy(int localPort) throws Exception {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", localPort));
        URL url = URI.create(properties.getProbeUrl()).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setConnectTimeout((int) properties.getProbeTimeoutMs());
        conn.setReadTimeout((int) properties.getProbeTimeoutMs());
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "v2board-external-probe");
        try {
            int code = conn.getResponseCode();
            // 连通即可，常见探针返回 204/200
            return code > 0 && code < 500;
        } finally {
            conn.disconnect();
        }
    }

    private static boolean waitPortOpen(int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return true;
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        return false;
    }

    private static int nextLocalPort() {
        int port = PORT_SEQ.getAndIncrement();
        if (port > 28000) {
            PORT_SEQ.set(18000);
        }
        return port;
    }

    private record ProbeResult(String fingerprint, boolean ok) {
    }
}
