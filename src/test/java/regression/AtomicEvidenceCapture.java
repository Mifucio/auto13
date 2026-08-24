package regression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class AtomicEvidenceCapture {
  private static final List<String> REQUIRED_COMPONENTS =
    List.of("dom", "screenshot", "accessibility", "geometry", "network");
  private static final Pattern CHECKPOINT_PATTERN = Pattern.compile(
    "^[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*){2}$"
  );
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final String checkpointId;
  private final Path stagingDirectory;
  private final Path finalDirectory;
  private final Map<String, EvidenceComponent> components = new LinkedHashMap<>();

  public AtomicEvidenceCapture(Path runDirectory, String checkpointId) {
    if (!CHECKPOINT_PATTERN.matcher(checkpointId).matches()) {
      throw new IllegalArgumentException("Invalid checkpoint ID: " + checkpointId);
    }
    this.checkpointId = checkpointId;
    this.finalDirectory = runDirectory.resolve("checkpoints").resolve(checkpointId);
    this.stagingDirectory = finalDirectory.resolveSibling(finalDirectory.getFileName() + ".staging");
  }

  public void start() throws IOException {
    deleteRecursively(stagingDirectory);
    Files.createDirectories(stagingDirectory);
  }

  public void captureJson(String component, JsonObject value) throws IOException {
    assertComponent(component);
    String artifact = component + ".json";
    Files.writeString(stagingDirectory.resolve(artifact), GSON.toJson(value) + "\n", StandardCharsets.UTF_8);
    components.put(component, EvidenceComponent.captured(artifact));
  }

  public void captureBinary(String component, byte[] value, String extension) throws IOException {
    assertComponent(component);
    String safeExtension = extension.replaceAll("[^a-zA-Z0-9]", "");
    String artifact = component + "." + safeExtension;
    Files.write(stagingDirectory.resolve(artifact), value);
    components.put(component, EvidenceComponent.captured(artifact));
  }

  public JsonObject finalizeCapture(JsonObject snapshotFields) throws IOException {
    List<String> missing = REQUIRED_COMPONENTS.stream().filter(component -> !components.containsKey(component)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Atomic snapshot is incomplete; missing: " + String.join(", ", missing));
    }

    JsonObject readiness = new JsonObject();
    readiness.addProperty("status", ReadinessStatus.READY.jsonValue);
    for (String component : REQUIRED_COMPONENTS) {
      readiness.add(component, components.get(component).toJson());
    }

    JsonObject snapshot = snapshotFields.deepCopy();
    snapshot.addProperty("schemaVersion", 1);
    snapshot.addProperty("checkpointId", checkpointId);
    snapshot.addProperty("capturedAt", Instant.now().toString());
    snapshot.addProperty("runtime", "java-selenide-cucumber-jvm");
    if (!snapshot.has("capabilities")) snapshot.add("capabilities", new JsonArray());
    if (!snapshot.has("errors")) snapshot.add("errors", new JsonArray());
    snapshot.add("readiness", readiness);
    Files.writeString(stagingDirectory.resolve("snapshot.json"), GSON.toJson(snapshot) + "\n", StandardCharsets.UTF_8);

    deleteRecursively(finalDirectory);
    Files.createDirectories(finalDirectory.getParent());
    try {
      Files.move(stagingDirectory, finalDirectory, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException error) {
      Files.move(stagingDirectory, finalDirectory);
    }
    return snapshot;
  }

  private static void assertComponent(String component) {
    if (!REQUIRED_COMPONENTS.contains(component)) {
      throw new IllegalArgumentException("Unknown evidence component: " + component);
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var paths = Files.walk(path)) {
      for (Path child : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
        Files.deleteIfExists(child);
      }
    }
  }

  public enum ReadinessStatus {
    READY("ready"), PARTIAL("partial"), FAILED("failed");

    private final String jsonValue;
    ReadinessStatus(String jsonValue) { this.jsonValue = jsonValue; }
  }

  private record EvidenceComponent(String status, String artifact, String capturedAt) {
    static EvidenceComponent captured(String artifact) {
      return new EvidenceComponent("captured", artifact, Instant.now().toString());
    }

    JsonObject toJson() {
      JsonObject value = new JsonObject();
      value.addProperty("status", status);
      value.addProperty("artifact", artifact);
      value.addProperty("capturedAt", capturedAt);
      return value;
    }
  }
}
