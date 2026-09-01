package runner;

import com.codeborne.selenide.WebDriverRunner;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TEMPORARY — rich browser-side performance analysis.
 * Captures navigation timing, resource timing, paint metrics, and element search
 * durations. Outputs a detailed markdown report to build/perf-analysis.md.
 *
 * Remove alongside TimelineMetricsPlugin once the scenario is accepted.
 */
public final class TemporaryPerfAnalyzer implements ConcurrentEventListener {

  private static final Path REPORT = Path.of("build", "perf-analysis.md");

  private final List<StepRecord> steps = new ArrayList<>();
  private String scenarioName = "unknown";
  private long scenarioStartNanos = 0L;
  private long scenarioStartWall = 0L;

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestCaseStarted.class, e -> {
      scenarioName = e.getTestCase().getName();
      scenarioStartNanos = System.nanoTime();
      scenarioStartWall = System.currentTimeMillis();
      System.out.printf("[perf] SCENARIO START \"%s\"%n", scenarioName);
    });
    publisher.registerHandlerFor(TestStepFinished.class, this::onStepFinished);
    publisher.registerHandlerFor(TestCaseFinished.class, this::onCaseFinished);
    publisher.registerHandlerFor(TestRunFinished.class, this::onRunFinished);
  }

  private void onStepFinished(TestStepFinished event) {
    if (!(event.getTestStep() instanceof PickleStepTestStep ps)) return;
    Duration d = event.getResult() != null ? event.getResult().getDuration() : null;
    double atSec = (System.nanoTime() - scenarioStartNanos) / 1e9;
    String keyword = ps.getStep() != null && ps.getStep().getKeyword() != null
        ? ps.getStep().getKeyword().trim() : "";
    String text = oneLine(ps.getStepText());
    String status = event.getResult() == null ? "UNKNOWN" : event.getResult().getStatus().name();
    long durMs = d != null ? d.toMillis() : 0L;

    BrowserSnapshot brow = new BrowserSnapshot();
    steps.add(new StepRecord(keyword, text, durMs, atSec, status, brow));

    System.out.printf("[perf] +%8.3fs %6d ms %-7s %s %s  |  %s%n",
        atSec, durMs, status, keyword, text, brow.summary());
  }

  private void onCaseFinished(TestCaseFinished event) {
    double totalSec = (System.nanoTime() - scenarioStartNanos) / 1e9;
    System.out.printf("[perf] SCENARIO END \"%s\" total=%.3fs status=%s%n",
        scenarioName, totalSec, event.getResult().getStatus());
  }

  private void onRunFinished(TestRunFinished event) {
    StringBuilder md = new StringBuilder();
    md.append("# Performance Analysis\n\n");
    md.append("Scenario: **").append(esc(scenarioName)).append("**\n\n");
    md.append("Generated: ").append(java.time.Instant.now()).append("\n\n");

    // ── Summary ──
    long totalStepMs = steps.stream().mapToLong(s -> s.durationMs).sum();
    md.append("## Summary\n\n");
    md.append("| Metric | Value |\n|---|---|\n");
    md.append("| Total steps | ").append(steps.size()).append(" |\n");
    md.append("| Total step wall time | ").append(totalStepMs).append(" ms |\n");
    md.append("| avg step | ").append(steps.isEmpty() ? 0 : totalStepMs / steps.size()).append(" ms |\n");

    long slowThreshold = 2000;
    long slowCount = steps.stream().filter(s -> s.durationMs >= slowThreshold).count();
    md.append("| Steps >= ").append(slowThreshold).append(" ms (slow) | ").append(slowCount).append(" |\n\n");

    // ── Step-by-step table ──
    md.append("## Step Timeline\n\n");
    md.append("| # | Step | Duration (ms) | T+ (s) | Status | Browser metrics |\n|---|---|---|---|---|---|\n");
    int i = 1;
    for (StepRecord r : steps) {
      md.append(String.format("| %d | %s %s | %d | %.3f | %s | %s |%n",
          i++, esc(r.keyword), esc(r.text), r.durationMs, r.atSeconds, r.status, r.browser.summary()));
    }

    // ── Slowest steps detail ──
    md.append("\n## Slowest Steps (bottleneck candidates)\n\n");
    List<StepRecord> sorted = steps.stream()
        .sorted((a, b) -> Long.compare(b.durationMs, a.durationMs))
        .limit(5)
        .collect(Collectors.toList());
    if (sorted.isEmpty()) {
      md.append("No steps recorded.\n");
    } else {
      md.append("| Rank | Step | Duration (ms) | Browser metrics |\n|---|---|---|---|\n");
      int rank = 1;
      for (StepRecord r : sorted) {
        md.append(String.format("| %d | %s %s | %d | %s |%n",
            rank++, esc(r.keyword), esc(r.text), r.durationMs, r.browser.summary()));
      }
    }

    // ── Navigation timing ──
    md.append("\n## Navigation Timing\n\n");
    BrowserSnapshot last = null;
    for (int si = steps.size() - 1; si >= 0; si--) {
      if (steps.get(si).browser.hasNav) { last = steps.get(si).browser; break; }
    }
    if (last != null && last.hasNav) {
      md.append("| Metric | Value (ms) |\n|---|---|\n");
      md.append("| DNS lookup | ").append(last.dns).append(" |\n");
      md.append("| TCP connect | ").append(last.tcp).append(" |\n");
      md.append("| TLS handshake | ").append(last.tls).append(" |\n");
      md.append("| TTFB (request→response) | ").append(last.ttfb).append(" |\n");
      md.append("| DOM content loaded | ").append(last.domContent).append(" |\n");
      md.append("| DOM complete (interactive→complete) | ").append(last.domComplete).append(" |\n");
      md.append("| Full page load | ").append(last.fullLoad).append(" |\n");
    } else {
      md.append("Navigation timing not captured (page may not have performed a full navigation during this scenario).\n");
    }

    // ── Resource timing breakdown ──
    md.append("\n## Resource Loading (by type)\n\n");
    if (last != null && last.hasResources) {
      md.append("| Type | Count | Total size (KB) | Total time (ms) |\n|---|---|---|---|\n");
      for (Map.Entry<String, long[]> e : last.resourceSummary.entrySet()) {
        md.append(String.format("| %s | %d | %d | %d |%n",
            e.getKey(), e.getValue()[0], e.getValue()[1] / 1024, e.getValue()[2]));
      }
      md.append("\n**Total resources loaded**: ").append(last.totalResources).append("\n");
    } else {
      md.append("Resource timing not captured.\n");
    }

    // ── Paint timing ──
    md.append("\n## Paint Timing\n\n");
    if (last != null && last.hasPaint) {
      md.append("| Metric | Value (ms) |\n|---|---|\n");
      md.append("| First Paint | ").append(last.firstPaint).append(" |\n");
      md.append("| First Contentful Paint (FCP) | ").append(last.fcp).append(" |\n");
      if (last.lcp > 0) md.append("| Largest Contentful Paint (LCP) | ").append(last.lcp).append(" |\n");
    } else {
      md.append("Paint timing not captured.\n");
    }

    try {
      Files.createDirectories(REPORT.toAbsolutePath().getParent());
      Files.writeString(REPORT, md.toString(), StandardCharsets.UTF_8);
      System.err.println("PERF_ANALYSIS_FILE " + REPORT.toAbsolutePath());
    } catch (Exception e) {
      System.err.println("PERF_ANALYSIS_WRITE_FAILED " + e);
    }
    System.out.printf("[perf] ANALYSIS WRITTEN: %s%n", REPORT.toAbsolutePath());
  }

  // ── Snapshot capture from browser ──

  private static final class BrowserSnapshot {
    boolean hasNav = false;
    long dns = -1, tcp = -1, tls = -1, ttfb = -1;
    long domContent = -1, domComplete = -1, fullLoad = -1;
    boolean hasPaint = false;
    long firstPaint = -1, fcp = -1, lcp = -1;
    boolean hasResources = false;
    Map<String, long[]> resourceSummary = new LinkedHashMap<>();
    int totalResources = 0;

    BrowserSnapshot() {
      try {
        if (!WebDriverRunner.hasWebDriverStarted()) return;
        WebDriver driver = WebDriverRunner.getWebDriver();
        if (!(driver instanceof JavascriptExecutor js)) return;

        // Navigation timing
        Map<String, Object> nav = (Map<String, Object>) js.executeScript(
            "var p = performance.timing || {}; return { " +
            "domainLookupEnd: p.domainLookupEnd || 0, domainLookupStart: p.domainLookupStart || 0, " +
            "connectEnd: p.connectEnd || 0, connectStart: p.connectStart || 0, " +
            "requestStart: p.requestStart || 0, responseStart: p.responseStart || 0, " +
            "domContentLoadedEventEnd: p.domContentLoadedEventEnd || 0, " +
            "domInteractive: p.domInteractive || 0, domComplete: p.domComplete || 0, " +
            "loadEventEnd: p.loadEventEnd || 0, navigationStart: p.navigationStart || 0 };");
        long navStart = toLong(nav.get("navigationStart"));
        if (navStart > 0) {
          hasNav = true;
          dns = diff(nav.get("domainLookupEnd"), nav.get("domainLookupStart"));
          tcp = diff(nav.get("connectEnd"), nav.get("connectStart"));
          long secureStart = toLong(nav.get("connectEnd")) > toLong(nav.get("connectStart"))
              && toLong(nav.get("domainLookupEnd")) > 0
              ? toLong(nav.get("connectEnd")) - toLong(nav.get("connectStart")) - dns : -1;
          tls = secureStart > 0 && toLong(nav.get("connectStart")) > 0
              ? toLong(nav.get("connectEnd")) - toLong(nav.get("connectStart")) - dns - tcp
              : -1;
          if (tls < 0) tls = -1;
          ttfb = diff(nav.get("responseStart"), nav.get("requestStart"));
          domContent = diff(nav.get("domContentLoadedEventEnd"), nav.get("navigationStart"));
          domComplete = diff(nav.get("domComplete"), nav.get("domInteractive"));
          long loadEnd = toLong(nav.get("loadEventEnd"));
          fullLoad = loadEnd > 0 ? loadEnd - navStart : -1;
        }

        // Paint timing
        List<Map<String, Object>> paints = (List<Map<String, Object>>) js.executeScript(
            "return performance.getEntriesByType('paint').map(function(e) { " +
            "return {name: e.name, startTime: e.startTime}; })");
        if (paints != null) {
          for (Map<String, Object> p : paints) {
            String name = (String) p.get("name");
            long val = toLong(p.get("startTime"));
            if ("first-paint".equals(name)) { firstPaint = val; hasPaint = true; }
            if ("first-contentful-paint".equals(name)) { fcp = val; hasPaint = true; }
          }
        }

        // LCP
        Object lcpRaw = js.executeScript(
            "var lcp = 0; try { var obs = new PerformanceObserver(function() {}); " +
            "var entries = performance.getEntriesByType('largest-contentful-paint'); " +
            "if (entries.length > 0) lcp = entries[entries.length - 1].renderTime || entries[entries.length - 1].loadTime; " +
            "} catch(e) {} return lcp;");
        if (lcpRaw instanceof Number) { lcp = ((Number) lcpRaw).longValue(); if (lcp > 0) hasPaint = true; }

        // Resource timing
        List<Map<String, Object>> resources = (List<Map<String, Object>>) js.executeScript(
            "return performance.getEntriesByType('resource').map(function(e) { " +
            "return {initiatorType: e.initiatorType, duration: e.duration, " +
            "transferSize: e.transferSize || 0}; })");
        if (resources != null && !resources.isEmpty()) {
          hasResources = true;
          totalResources = resources.size();
          for (Map<String, Object> r : resources) {
            String type = (String) r.get("initiatorType");
            if (type == null) type = "other";
            long dur = toLong(r.get("duration"));
            long size = toLong(r.get("transferSize"));
            resourceSummary.compute(type, (k, v) -> {
              if (v == null) return new long[]{1, size, dur};
              v[0]++; v[1] += size; v[2] += dur; return v;
            });
          }
        }

      } catch (Exception e) {
        // Browser not ready or closed — skip
      }
    }

    String summary() {
      List<String> parts = new ArrayList<>();
      if (hasNav && fullLoad > 0) parts.add("load=" + fullLoad + "ms");
      if (hasPaint) { parts.add("FCP=" + fcp + "ms"); if (lcp > 0) parts.add("LCP=" + lcp + "ms"); }
      if (hasResources) parts.add("res=" + totalResources);
      return parts.isEmpty() ? "" : String.join(" ", parts);
    }

    private static long toLong(Object v) {
      if (v instanceof Number) return ((Number) v).longValue();
      return 0L;
    }
    private static long diff(Object end, Object start) {
      long e = toLong(end), s = toLong(start);
      return (e > 0 && s > 0) ? e - s : -1;
    }
  }

  // ── Data records ──

  private static final class StepRecord {
    final String keyword, text, status;
    final long durationMs;
    final double atSeconds;
    final BrowserSnapshot browser;
    StepRecord(String k, String t, long d, double a, String s, BrowserSnapshot b) {
      keyword = k; text = t; durationMs = d; atSeconds = a; status = s; browser = b;
    }
  }

  private static String oneLine(String s) {
    return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
  }
  private static String esc(String s) {
    return oneLine(s).replace("|", "\\|");
  }
}