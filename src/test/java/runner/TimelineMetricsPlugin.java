package runner;

import com.codeborne.selenide.WebDriverRunner;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * TEMPORARY session aid — per-step execution timeline plus pacing:
 * after every real (pickle) step that leaves the browser off /login, pause
 * STEP_PACING_MS (default 2000) so a human can follow the headed run.
 * Remove this class and its TestRunner entry on scenario acceptance.
 */
public final class TimelineMetricsPlugin implements ConcurrentEventListener {

  private static final Path OUT_MD = Path.of("build", "timeline-metrics.md");
  private static final long PACING_MS = configuredPacingMs();

  private final List<Record> records = new ArrayList<>();
  private String scenario = "unknown";
  private long caseStartNanos = 0L;

  private static long configuredPacingMs() {
    String v = System.getenv("STEP_PACING_MS");
    if (v == null || v.isBlank()) return 0L; // default: no pacing
    try { return Math.max(0L, Long.parseLong(v.trim())); } catch (NumberFormatException e) { return 0L; }
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestCaseStarted.class, e -> {
      scenario = e.getTestCase().getName();
      caseStartNanos = System.nanoTime();
      System.out.printf("[timeline] SCENARIO START \"%s\"%n", scenario);
    });
    publisher.registerHandlerFor(TestStepFinished.class, this::onStepFinished);
    publisher.registerHandlerFor(TestCaseFinished.class, e ->
      System.out.printf("[timeline] SCENARIO END   \"%s\" total=%.3fs status=%s%n",
        e.getTestCase().getName(), (System.nanoTime() - caseStartNanos) / 1e9,
        e.getResult().getStatus()));
    publisher.registerHandlerFor(TestRunFinished.class, this::onRunFinished);
  }

  private void onStepFinished(TestStepFinished event) {
    if (!(event.getTestStep() instanceof PickleStepTestStep)) return;
    PickleStepTestStep ps = (PickleStepTestStep) event.getTestStep();
    Duration d = event.getResult() != null ? event.getResult().getDuration() : null;
    long durMs = d != null ? d.toMillis() : 0L;
    double atSec = (System.nanoTime() - caseStartNanos) / 1e9;
    String keyword = ps.getStep() != null && ps.getStep().getKeyword() != null ? ps.getStep().getKeyword().trim() : "";
    String text = oneLine(ps.getStepText());
    String status = event.getResult() == null ? "UNKNOWN" : event.getResult().getStatus().name();
    synchronized (records) { records.add(new Record(scenario, keyword, text, durMs, atSec, status)); }
    System.out.printf("[timeline] +%8.3fs  %6d ms  %-7s %s %s%n", atSec, durMs, status, keyword, text);
    paceIfPostLogin();
  }

  private void paceIfPostLogin() {
    if (PACING_MS <= 0) return;
    try {
      String url = WebDriverRunner.url();
      if (url == null || url.contains("/login") || !url.startsWith("http")) return;
      Thread.sleep(PACING_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable ignored) {
      // no driver yet or already closed
    }
  }

  private void onRunFinished(TestRunFinished event) {
    List<Record> snapshot;
    synchronized (records) { snapshot = new ArrayList<>(records); }
    StringBuilder md = new StringBuilder("# Execution timeline metrics\n\n")
      .append("Run finished: ").append(event.getInstant()).append("\n")
      .append("Result: ").append(event.getResult()).append("\n")
      .append("Step pacing: ").append(PACING_MS).append(" ms (STEP_PACING_MS)\n\n")
      .append("| # | Scenario | Step | Duration ms | T+ seconds | Status |\n|---|---|---|---|---|---|\n");
    long totalMs = 0;
    int i = 1;
    for (Record r : snapshot) {
      md.append(String.format("| %d | %s | %s %s | %d | %.3f | %s |%n",
        i++, esc(r.scenario), esc(r.keyword), esc(r.text), r.durationMs, r.atSeconds, r.status));
      totalMs += r.durationMs;
    }
    md.append(String.format("%n**Total step time**: %d ms across %d steps%n", totalMs, snapshot.size()));
    try {
      Files.createDirectories(OUT_MD.toAbsolutePath().getParent());
      Files.writeString(OUT_MD, md.toString(), StandardCharsets.UTF_8);
      System.err.println("TIMELINE_METRICS_FILE " + OUT_MD.toAbsolutePath());
    } catch (Exception e) {
      System.err.println("TIMELINE_METRICS_WRITE_FAILED " + e);
    }
    System.out.printf("[timeline] RUN FINISHED: %d steps, total step time %d ms, result=%s%n",
      snapshot.size(), totalMs, event.getResult());
  }

  private static String oneLine(String s) { return s == null ? "" : s.replace('\n', ' ').replace('\r', ' '); }
  private static String esc(String s) { return oneLine(s).replace("|", "\\|"); }

  private static final class Record {
    final String scenario, keyword, text, status;
    final long durationMs; final double atSeconds;
    Record(String s, String k, String t, long d, double a, String st) {
      scenario = s; keyword = k; text = t; durationMs = d; atSeconds = a; status = st;
    }
  }
}
