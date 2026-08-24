package runner;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestStepFinished;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists Cucumber failures independently from Gradle's replaceable HTML report.
 * A later filtered run may overwrite build/reports/tests/test, but these files
 * remain under build/failure-logs/<run-id>/ and the root cause is also printed
 * to stdout as one FAILURE_SUMMARY block per failed scenario.
 */
public final class FailureLoggingPlugin implements ConcurrentEventListener {
  private static final DateTimeFormatter RUN_ID_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
  private static final String RUN_ID = configuredRunId();
  private static final Path RUN_DIR = Path.of("build", "failure-logs", RUN_ID).toAbsolutePath().normalize();
  private static final Map<String, StepFailure> FIRST_STEP_FAILURE = new ConcurrentHashMap<>();

  public FailureLoggingPlugin() {
    System.err.println("FAILURE_LOG_DIR " + RUN_DIR);
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestStepFinished.class, this::onStepFinished);
    publisher.registerHandlerFor(TestCaseFinished.class, this::onCaseFinished);
  }

  private void onStepFinished(TestStepFinished event) {
    if (event.getResult().getStatus() != Status.FAILED || event.getResult().getError() == null) return;
    if (!(event.getTestStep() instanceof PickleStepTestStep step)) return;
    String key = scenarioKey(event.getTestCase().getUri().toString(), event.getTestCase().getName());
    FIRST_STEP_FAILURE.putIfAbsent(key,
      new StepFailure(step.getStep().getKeyword().trim() + " " + step.getStep().getText(), event.getResult().getError()));
  }

  private void onCaseFinished(TestCaseFinished event) {
    if (event.getResult().getStatus() != Status.FAILED) return;

    String feature = event.getTestCase().getUri().toString();
    String scenario = event.getTestCase().getName();
    String key = scenarioKey(feature, scenario);
    StepFailure stepFailure = FIRST_STEP_FAILURE.remove(key);
    Throwable error = stepFailure != null ? stepFailure.error() : event.getResult().getError();
    String step = stepFailure != null ? stepFailure.step() : "<unknown step>";
    String root = rootMessage(error);

    String header = "FAILURE_SUMMARY feature=" + feature
      + " scenario=\"" + oneLine(scenario) + "\""
      + " step=\"" + oneLine(step) + "\""
      + " root=\"" + oneLine(root) + "\"";
    System.err.println("\n" + header);
    if (error != null) error.printStackTrace(System.err);

    try {
      Files.createDirectories(RUN_DIR);
      Path scenarioFile = RUN_DIR.resolve(slug(feature + "-" + scenario) + ".log");
      StringBuilder body = new StringBuilder();
      body.append(header).append(System.lineSeparator());
      body.append("run_id=").append(RUN_ID).append(System.lineSeparator());
      body.append("feature=").append(feature).append(System.lineSeparator());
      body.append("scenario=").append(scenario).append(System.lineSeparator());
      body.append("step=").append(step).append(System.lineSeparator());
      if (error != null) body.append(stackTrace(error));
      Files.writeString(scenarioFile, body.toString(), StandardCharsets.UTF_8);
      Files.writeString(RUN_DIR.resolve("summary.txt"), header + System.lineSeparator(), StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
      System.err.println("FAILURE_LOG_FILE " + scenarioFile);
    } catch (Exception writeFailure) {
      System.err.println("FAILURE_LOG_WRITE_FAILED " + writeFailure.getClass().getSimpleName());
    }
  }

  public static String runId() {
    return RUN_ID;
  }

  public static Path runDirectory() {
    return RUN_DIR;
  }

  private static String configuredRunId() {
    String explicit = System.getProperty("auto13.runId", "").trim();
    if (explicit.isEmpty()) explicit = System.getenv().getOrDefault("TEST_RUN_ID", "").trim();
    return explicit.isEmpty() ? RUN_ID_FORMAT.format(Instant.now()) : slug(explicit);
  }

  private static String scenarioKey(String feature, String scenario) {
    return feature + "|" + scenario;
  }

  private static String rootMessage(Throwable error) {
    if (error == null) return "<no throwable>";
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    String message = root.getMessage();
    return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
  }

  private static String stackTrace(Throwable error) {
    StringWriter buffer = new StringWriter();
    error.printStackTrace(new PrintWriter(buffer));
    return buffer.toString();
  }

  private static String oneLine(String value) {
    if (value == null) return "";
    String text = value.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    return text.length() <= 1000 ? text : text.substring(0, 1000) + "...";
  }

  private static String slug(String value) {
    if (value == null || value.isBlank()) return "unknown";
    String slug = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-");
    if (slug.length() > 160) slug = slug.substring(slug.length() - 160);
    return slug.replaceAll("^-|-$", "");
  }

  private record StepFailure(String step, Throwable error) { }
}
