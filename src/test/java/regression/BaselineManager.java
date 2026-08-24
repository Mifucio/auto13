package regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

public final class BaselineManager {
  private BaselineManager() { }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) throw new IllegalArgumentException("Expected capture, approve, or compare");
    if ("capture".equals(args[0])) capture(System.getProperty("runId", "local"), System.getProperty("baselineId", "candidate-local"));
    else if ("approve".equals(args[0])) approve(System.getProperty("candidateId", System.getProperty("baselineId")), System.getProperty("baselineId"));
    else if ("compare".equals(args[0])) compare(System.getProperty("runId", "local"), System.getProperty("baselineId"));
    else throw new IllegalArgumentException("Unknown command: " + args[0]);
  }

  private static void capture(String runId, String baselineId) throws IOException {
    Path source = Path.of("reports", "runs", runId, "checkpoints");
    if (!Files.isDirectory(source)) throw new IllegalStateException("Run checkpoints not found: " + source);
    Path target = Path.of("regression", "baseline-candidates", baselineId, "checkpoints");
    copyTree(source, target);
    JsonObject manifest = new JsonObject();
    manifest.addProperty("schemaVersion", 1);
    manifest.addProperty("status", "candidate");
    manifest.addProperty("baselineId", baselineId);
    manifest.addProperty("createdAt", Instant.now().toString());
    manifest.addProperty("target", System.getProperty("target", "local"));
    manifest.addProperty("suiteRevision", System.getProperty("suiteRevision", "working-tree"));
    try (var checkpoints = Files.list(source)) {
      manifest.addProperty("checkpoints", checkpoints.filter(Files::isDirectory).count());
    }
    manifest.addProperty("artifactDigest", digestDirectory(target));
    Files.writeString(target.getParent().resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n");
  }

  private static void approve(String candidateId, String baselineId) throws IOException {
    if (candidateId == null || candidateId.isBlank() || baselineId == null || baselineId.isBlank()) throw new IllegalArgumentException("candidateId and baselineId are required");
    Path candidate = Path.of("regression", "baseline-candidates", candidateId);
    Path candidateCheckpoints = candidate.resolve("checkpoints");
    Path manifestPath = candidate.resolve("manifest.json");
    if (!Files.isDirectory(candidateCheckpoints) || !Files.isRegularFile(manifestPath)) throw new IllegalStateException("Baseline candidate not found: " + candidate);
    JsonObject candidateManifest = JsonParser.parseString(Files.readString(manifestPath)).getAsJsonObject();
    String observedDigest = digestDirectory(candidateCheckpoints);
    if (!observedDigest.equals(candidateManifest.get("artifactDigest").getAsString())) throw new IllegalStateException("Baseline candidate digest mismatch: expected " + candidateManifest.get("artifactDigest").getAsString() + " observed " + observedDigest);
    Path approved = Path.of("regression", "baselines", baselineId);
    if (Files.exists(approved)) throw new IllegalStateException("Approved baseline already exists: " + approved);
    Path staging = approved.getParent().resolve("." + baselineId + ".staging-" + ProcessHandle.current().pid() + "-" + System.nanoTime());
    Files.createDirectories(staging);
    try {
      copyTree(candidateCheckpoints, staging.resolve("checkpoints"));
      String approvedDigest = digestDirectory(staging.resolve("checkpoints"));
      if (!approvedDigest.equals(observedDigest)) throw new IllegalStateException("Approved baseline copy digest mismatch");
      JsonObject approvedManifest = new JsonObject();
      approvedManifest.addProperty("schemaVersion", candidateManifest.has("schemaVersion") ? candidateManifest.get("schemaVersion").getAsInt() : 1);
      approvedManifest.addProperty("baselineId", baselineId);
      approvedManifest.addProperty("createdAt", candidateManifest.has("createdAt") ? candidateManifest.get("createdAt").getAsString() : Instant.now().toString());
      approvedManifest.addProperty("approvedAt", Instant.now().toString());
      approvedManifest.addProperty("approvedBy", System.getProperty("approvedBy", System.getenv().getOrDefault("TEST_APPROVED_BY", System.getProperty("user.name", "unknown"))));
      approvedManifest.addProperty("target", System.getProperty("target", candidateManifest.has("target") ? candidateManifest.get("target").getAsString() : "local"));
      approvedManifest.addProperty("applicationVersion", System.getProperty("applicationVersion", System.getenv().getOrDefault("TEST_APPLICATION_VERSION", candidateManifest.has("applicationVersion") ? candidateManifest.get("applicationVersion").getAsString() : "unknown")));
      approvedManifest.addProperty("gitRevision", System.getProperty("gitRevision", System.getenv().getOrDefault("TEST_GIT_REVISION", candidateManifest.has("gitRevision") ? candidateManifest.get("gitRevision").getAsString() : "working-tree")));
      approvedManifest.addProperty("suiteRevision", System.getProperty("suiteRevision", candidateManifest.has("suiteRevision") ? candidateManifest.get("suiteRevision").getAsString() : "working-tree"));
      approvedManifest.addProperty("checkpoints", candidateManifest.get("checkpoints").getAsLong());
      approvedManifest.addProperty("artifactDigest", approvedDigest);
      Files.writeString(staging.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(approvedManifest) + "\n");
      Files.move(staging, approved, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception error) {
      deleteTree(staging);
      throw error;
    }
  }

  private static void compare(String runId, String baselineId) throws IOException {
    if (baselineId == null || baselineId.isBlank()) throw new IllegalArgumentException("baselineId is required");
    Path baselineRoot = Path.of("regression", "baselines", baselineId, "checkpoints");
    if (!Files.isDirectory(baselineRoot)) throw new IllegalStateException("Approved baseline not found: " + baselineRoot);
    Path currentRoot = Path.of("reports", "runs", runId, "checkpoints");
    JsonArray findings = new JsonArray();
    JsonArray visualComparisons = new JsonArray();
    SemanticComparator comparator = new SemanticComparator();
    VisualComparator visualComparator = new VisualComparator();
    double visualThreshold = Double.parseDouble(System.getProperty("visualThreshold", System.getenv().getOrDefault("TEST_VISUAL_THRESHOLD", "0.02")));
    int colorTolerance = Integer.parseInt(System.getProperty("colorTolerance", System.getenv().getOrDefault("TEST_COLOR_TOLERANCE", "0")));
    int checkpointCount = 0;
    if (Files.isDirectory(baselineRoot)) {
      for (Path baseline : Files.list(baselineRoot).filter(Files::isDirectory).sorted().toList()) {
        checkpointCount++;
        String checkpointId = baseline.getFileName().toString();
        Path currentDirectory = currentRoot.resolve(baseline.getFileName());
        Path current = currentDirectory.resolve("snapshot.json");
        JsonObject visual = visualComparator.compare(checkpointId, baseline.resolve("screenshot.png"), currentDirectory.resolve("screenshot.png"), currentDirectory.resolve("screenshot.diff.png"), visualThreshold, colorTolerance);
        visualComparisons.add(visual);
        JsonObject expected = JsonParser.parseString(Files.readString(baseline.resolve("snapshot.json"))).getAsJsonObject();
        if (Files.exists(current)) {
          findings.addAll(comparator.compare(expected, JsonParser.parseString(Files.readString(current)).getAsJsonObject()));
          if ("compared".equals(visual.get("status").getAsString()) && !visual.get("passed").getAsBoolean()) {
            JsonObject changed = new JsonObject();
            changed.addProperty("findingId", checkpointId + ":content_change");
            changed.addProperty("checkpointId", checkpointId);
            changed.addProperty("category", "content_change");
            changed.addProperty("severity", "high");
            changed.addProperty("title", "Visual change ratio " + visual.get("changedRatio").getAsDouble() + " exceeds threshold " + visualThreshold);
            changed.addProperty("decisionSource", "deterministic");
            changed.addProperty("confidence", 1);
            findings.add(changed);
          }
        }
        else {
          JsonObject blocked = new JsonObject();
          blocked.addProperty("findingId", baseline.getFileName() + ":blocked");
          blocked.addProperty("checkpointId", baseline.getFileName().toString());
          blocked.addProperty("category", "blocked");
          blocked.addProperty("severity", "critical");
          blocked.addProperty("decisionSource", "deterministic");
          findings.add(blocked);
        }
      }
    }
    JsonObject results = new JsonObject();
    results.addProperty("schemaVersion", 1);
    results.addProperty("baselineId", baselineId);
    results.addProperty("currentRunId", runId);
    Set<String> affected = new HashSet<>();
    int blockedCount = 0;
    for (var item : findings) {
      JsonObject finding = item.getAsJsonObject();
      affected.add(finding.get("checkpointId").getAsString());
      if ("blocked".equals(finding.get("category").getAsString())) blockedCount++;
    }
    JsonObject summary = new JsonObject();
    summary.addProperty("checkpoints", checkpointCount);
    summary.addProperty("passed", checkpointCount - affected.size());
    summary.addProperty("changed", findings.size());
    summary.addProperty("regressions", findings.size());
    summary.addProperty("blocked", blockedCount);
    summary.addProperty("unknown", 0);
    results.add("summary", summary);
    results.add("visualComparisons", visualComparisons);
    results.add("findings", findings);
    Path output = Path.of("reports", "runs", runId, "regression-results.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(results) + "\n");
    RegressionReport.write(baselineId, runId, baselineRoot, currentRoot, results, output.getParent().resolve("regression-report.html"));
    if (!findings.isEmpty()) throw new AssertionError("Regression comparison found " + findings.size() + " finding(s); results: " + output);
  }

  private static void copyTree(Path source, Path target) throws IOException {
    try (var paths = Files.walk(source)) {
      for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
        Path destination = target.resolve(source.relativize(path));
        if (Files.isDirectory(path)) Files.createDirectories(destination);
        else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }

  private static String digestDirectory(Path root) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var paths = Files.walk(root)) {
        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
          digest.update(root.relativize(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          digest.update(Files.readAllBytes(path));
        }
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }
}
