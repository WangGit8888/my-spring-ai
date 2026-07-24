package com.example.myspringai.bench;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 预警接口压测工具。
 * <p>
 * 用法（命令行直接跑 main，无需启动 Spring）：
 * <pre>
 *   java AlarmLoadTest.java --qps=1000 --duration=60
 * </pre>
 *
 * <p>会自动跑两轮：
 * <ol>
 *   <li>压测：用随机 alarmId 按目标 QPS 发请求，统计吞吐和延迟</li>
 *   <li>幂等验证：同一 alarmId 发 100 次，预期 1 次 success + 99 次 duplicate</li>
 * </ol>
 */
public class AlarmLoadTest {

    private static final String[] ALARM_TYPES = {"区域入侵", "越界", "徘徊", "烟火检测", "安全帽识别", "周界入侵", "温度异常"};
    private static final String[] DEVICE_PREFIXES = {"CAM", "SEN", "DET", "NVR"};

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        int qps = 1000;
        int durationSec = 60;
        String baseUrl = "http://localhost:9999/api/hikvision/alarm";

        for (String arg : args) {
            if (arg.startsWith("--qps=")) qps = Integer.parseInt(arg.substring(6));
            if (arg.startsWith("--duration=")) durationSec = Integer.parseInt(arg.substring(11));
            if (arg.startsWith("--url=")) baseUrl = arg.substring(6);
        }

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       预警接口压测工具                    ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.printf("║ 目标 QPS: %-5d    持续: %-3d 秒        ║\n", qps, durationSec);
        System.out.printf("║ 接口: %-32s ║\n", baseUrl);
        System.out.println("╚══════════════════════════════════════════╝");

        // 先快速连通性检查
        if (!healthCheck(baseUrl)) {
            System.err.println("连通性检查失败，请确认服务已启动: " + baseUrl);
            System.exit(1);
        }

        // ---- 第一轮：QPS 压测 ----
        System.out.println("\n>>> 第一轮：QPS 压测（随机 alarmId）\n");
        runLoadTest(baseUrl, qps, durationSec);

        // ---- 第二轮：幂等验证 ----
        System.out.println("\n>>> 第二轮：幂等验证（固定 alarmId × 100）\n");
        runIdempotencyTest(baseUrl);

        System.out.println("\n压测完成。可查询 DB 确认: SELECT COUNT(*), notify_status FROM alert_info GROUP BY notify_status;");
    }

    // ==================== HTTP 客户端 ====================

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static boolean healthCheck(String baseUrl) {
        try {
            Map<String, Object> body = buildRequestBody("health-check-" + UUID.randomUUID());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("连通性检查: HTTP " + resp.statusCode() + " " + resp.body());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            System.out.println("连通性检查异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== 压测主逻辑 ====================

    private static void runLoadTest(String baseUrl, int targetQps, int durationSec) throws Exception {
        int totalRequests = targetQps * durationSec;

        // 令牌桶：每秒补充 targetQps 个令牌
        Semaphore bucket = new Semaphore(targetQps);
        ScheduledExecutorService replenisher = Executors.newSingleThreadScheduledExecutor();
        replenisher.scheduleAtFixedRate(() -> {
            int toAdd = targetQps - bucket.availablePermits();
            if (toAdd > 0) bucket.release(toAdd);
        }, 0, 1, TimeUnit.SECONDS);

        // 工作线程池
        int workerCount = Math.min(targetQps, 500);
        ExecutorService workers = Executors.newFixedThreadPool(workerCount);

        // 统计
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        long startTime = System.currentTimeMillis();
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();

        // 每 10 秒打印报告
        reporter.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            int total = successCount.get() + duplicateCount.get() + errorCount.get();
            if (elapsed == 0) elapsed = 1;
            System.out.printf("[%3ds] 已发:%d 成功:%d 重复:%d 失败:%d | 实时QPS:%d | P50:%.0fms P99:%.0fms\n",
                    elapsed, total, successCount.get(), duplicateCount.get(), errorCount.get(),
                    total / elapsed,
                    percentile(latencies, 50),
                    percentile(latencies, 99));
        }, 10, 10, TimeUnit.SECONDS);

        // 提交任务
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            bucket.acquire(); // 等令牌 → 控制 QPS
            futures.add(workers.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    String body = MAPPER.writeValueAsString(buildRequestBody(null));
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                    long latencyNs = System.nanoTime() - t0;
                    latencies.offer(latencyNs / 1_000_000); // 转 ms

                    if (resp.body().contains("success")) {
                        successCount.incrementAndGet();
                    } else if (resp.body().contains("duplicate")) {
                        duplicateCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    long latencyNs = System.nanoTime() - t0;
                    latencies.offer(latencyNs / 1_000_000);
                }
            }));
        }

        // 等待所有任务完成
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }

        reporter.shutdown();
        replenisher.shutdown();
        workers.shutdown();
        workers.awaitTermination(10, TimeUnit.SECONDS);

        // 最终报告
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsed == 0) elapsed = 1;
        int total = successCount.get() + duplicateCount.get() + errorCount.get();

        System.out.println("\n═══════════════════ 最终报告 ═══════════════════");
        System.out.printf("  总耗时:      %d 秒\n", elapsed);
        System.out.printf("  总请求:      %d\n", total);
        System.out.printf("  成功(新告警): %d\n", successCount.get());
        System.out.printf("  重复:        %d\n", duplicateCount.get());
        System.out.printf("  失败:        %d\n", errorCount.get());
        System.out.printf("  实际吞吐:    %.1f req/s\n", (double) total / elapsed);
        System.out.println("──────────────── 延迟分布 ──────────────────────");
        System.out.printf("  P50:  %.0f ms\n", percentile(latencies, 50));
        System.out.printf("  P90:  %.0f ms\n", percentile(latencies, 90));
        System.out.printf("  P99:  %.0f ms\n", percentile(latencies, 99));
        System.out.printf("  P999: %.0f ms\n", percentile(latencies, 99.9));
        System.out.printf("  Max:  %d ms\n", latencies.stream().max(Long::compare).orElse(0L));
        System.out.println("══════════════════════════════════════════════════");
    }

    // ==================== 幂等验证 ====================

    private static void runIdempotencyTest(String baseUrl) throws Exception {
        String repeatAlarmId = "bench-repeat-" + UUID.randomUUID().toString().substring(0, 8);
        int success = 0, duplicate = 0, error = 0;

        for (int i = 0; i < 100; i++) {
            try {
                String body = MAPPER.writeValueAsString(buildRequestBody(repeatAlarmId));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.body().contains("success")) success++;
                else if (resp.body().contains("duplicate")) duplicate++;
                else error++;
            } catch (Exception e) {
                error++;
            }
        }

        System.out.printf("  幂等测试: alarmId=%s\n", repeatAlarmId);
        System.out.printf("  success: %d  |  duplicate: %d  |  error: %d\n", success, duplicate, error);

        if (success == 1 && duplicate == 99) {
            System.out.println("  ✓ 幂等正确：唯一一次成功，其余 99 次正确拦截");
        } else {
            System.out.println("  ✗ 幂等异常！预期 1 success + 99 duplicate");
        }
    }

    // ==================== 工具方法 ====================

    private static Map<String, Object> buildRequestBody(String alarmId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("alarmId", alarmId != null ? alarmId : UUID.randomUUID().toString());
        body.put("alarmType", ALARM_TYPES[ThreadLocalRandom.current().nextInt(ALARM_TYPES.length)]);
        body.put("alarmContent", "压测告警内容 - " + LocalDateTime.now().format(DT_FMT));
        body.put("alarmTime", LocalDateTime.now().format(DT_FMT));
        body.put("deviceId", DEVICE_PREFIXES[ThreadLocalRandom.current().nextInt(DEVICE_PREFIXES.length)]
                + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
        body.put("deviceName", "测试设备-" + ThreadLocalRandom.current().nextInt(100));
        body.put("alarmLevel", ThreadLocalRandom.current().nextInt(3) + 1);
        body.put("rawData", Map.of(
                "channel", ThreadLocalRandom.current().nextInt(64) + 1,
                "regionId", ThreadLocalRandom.current().nextInt(10) + 1,
                "confidence", ThreadLocalRandom.current().nextInt(80, 100)
        ));
        return body;
    }

    private static double percentile(Collection<Long> samples, double p) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return sorted.get(idx);
    }
}
