package regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SemanticComparator {
  public enum Category { BLOCKED, MISSING_CAPABILITY, CHANGED_ROLE, NOT_VISIBLE, NOT_INTERACTIVE }

  public JsonArray compare(JsonObject baseline, JsonObject current) {
    String checkpointId = baseline.get("checkpointId").getAsString();
    JsonArray findings = new JsonArray();
    if (!checkpointId.equals(current.get("checkpointId").getAsString()) || !ready(baseline) || !ready(current)) {
      findings.add(finding(checkpointId, Category.BLOCKED, "critical"));
      return findings;
    }
    Map<String, JsonObject> observed = new LinkedHashMap<>();
    for (var item : current.getAsJsonArray("capabilities")) {
      JsonObject capability = item.getAsJsonObject();
      observed.put(capability.get("id").getAsString(), capability);
    }
    for (var item : baseline.getAsJsonArray("capabilities")) {
      JsonObject expected = item.getAsJsonObject();
      String id = expected.get("id").getAsString();
      JsonObject actual = observed.get(id);
      if (actual == null) { findings.add(finding(checkpointId, Category.MISSING_CAPABILITY, "critical")); continue; }
      if (expected.has("role") && !expected.get("role").getAsString().equals(actual.has("role") ? actual.get("role").getAsString() : "")) findings.add(finding(checkpointId, Category.CHANGED_ROLE, "high"));
      if (expected.get("visible").getAsBoolean() && !actual.get("visible").getAsBoolean()) findings.add(finding(checkpointId, Category.NOT_VISIBLE, "high"));
      if (expected.get("interactive").getAsBoolean() && !actual.get("interactive").getAsBoolean()) findings.add(finding(checkpointId, Category.NOT_INTERACTIVE, "critical"));
    }
    return findings;
  }

  private boolean ready(JsonObject snapshot) {
    return snapshot.has("readiness") && "ready".equals(snapshot.getAsJsonObject("readiness").get("status").getAsString());
  }

  private JsonObject finding(String checkpointId, Category category, String severity) {
    JsonObject result = new JsonObject();
    result.addProperty("findingId", checkpointId + ":" + category.name().toLowerCase());
    result.addProperty("checkpointId", checkpointId);
    result.addProperty("category", category.name().toLowerCase());
    result.addProperty("severity", severity);
    result.addProperty("decisionSource", "deterministic");
    result.addProperty("confidence", 1);
    return result;
  }
}
