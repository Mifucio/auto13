package regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class RegressionReport {
  private RegressionReport() { }

  public static void main(String[] args) throws Exception {
    String runId = System.getProperty("runId", "local");
    String baselineId = System.getProperty("baselineId");
    if (baselineId == null || baselineId.isBlank()) throw new IllegalArgumentException("baselineId is required");
    Path currentRoot = Path.of("reports", "runs", runId);
    JsonObject results = JsonParser.parseString(Files.readString(currentRoot.resolve("regression-results.json"))).getAsJsonObject();
    write(baselineId, runId, Path.of("regression", "baselines", baselineId, "checkpoints"), currentRoot.resolve("checkpoints"), results, currentRoot.resolve("regression-report.html"));
  }

  public static void write(String baselineId, String runId, Path baselineRoot, Path currentRoot, JsonObject results, Path output) throws IOException {
    Map<String, List<JsonObject>> findings = new LinkedHashMap<>();
    for (var item : results.getAsJsonArray("findings")) {
      JsonObject finding = item.getAsJsonObject();
      findings.computeIfAbsent(finding.get("checkpointId").getAsString(), ignored -> new ArrayList<>()).add(finding);
    }
    Set<String> checkpointIds = new TreeSet<>();
    collectDirectories(baselineRoot, checkpointIds);
    collectDirectories(currentRoot, checkpointIds);
    JsonObject summary = results.getAsJsonObject("summary");
    StringBuilder html = new StringBuilder("<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Regression Pack ")
      .append(escapeHtml(runId)).append("</title><style>:root{color-scheme:dark;font-family:Inter,system-ui,sans-serif;background:#0b1020;color:#e8ecf4}body{max-width:1440px;margin:auto;padding:32px}header,.checkpoint{background:#141b2d;border:1px solid #29334d;border-radius:16px;padding:24px;margin-bottom:20px}.summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px}.metric{background:#0e1527;padding:16px;border-radius:12px}.metric b{display:block;font-size:1.8rem}.images{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}figure{margin:0}figcaption{font-weight:700;margin-bottom:8px}img,.missing{width:100%;min-height:180px;object-fit:contain;background:#080c16;border-radius:10px}.missing{display:grid;place-items:center;color:#8792aa}.pass{color:#70db9b}code{color:#8cc8ff}details{margin-top:16px}</style></head><body><header><h1>Regression Pack</h1><p>Baseline <code>")
      .append(escapeHtml(baselineId)).append("</code> vs run <code>").append(escapeHtml(runId)).append("</code></p><div class='summary'>");
    for (String key : List.of("checkpoints", "passed", "changed", "regressions", "blocked", "unknown")) {
      html.append("<div class='metric'><span>").append(escapeHtml(key)).append("</span><b>").append(summary.has(key) ? summary.get(key).getAsInt() : 0).append("</b></div>");
    }
    html.append("</div></header>");
    for (String checkpointId : checkpointIds) {
      Path baselineDirectory = baselineRoot.resolve(checkpointId);
      Path currentDirectory = currentRoot.resolve(checkpointId);
      html.append("<section class='checkpoint'><h2>").append(escapeHtml(checkpointId)).append("</h2><div class='images'>")
        .append(renderImage("Baseline", baselineDirectory.resolve("screenshot.png")))
        .append(renderImage("Current", currentDirectory.resolve("screenshot.png")))
        .append(renderImage("Visual diff", currentDirectory.resolve("screenshot.diff.png")))
        .append("</div><h3>Findings</h3><ul>");
      List<JsonObject> checkpointFindings = findings.getOrDefault(checkpointId, List.of());
      if (checkpointFindings.isEmpty()) html.append("<li class='pass'>No semantic regression</li>");
      for (JsonObject finding : checkpointFindings) {
        html.append("<li><strong>").append(escapeHtml(value(finding, "severity"))).append("</strong> <code>").append(escapeHtml(value(finding, "category"))).append("</code> ").append(escapeHtml(value(finding, "title"))).append("</li>");
      }
      Set<String> files = new TreeSet<>();
      Set<String> currentFiles = new TreeSet<>();
      collectFiles(baselineDirectory, files);
      collectFiles(currentDirectory, currentFiles);
      html.append("</ul><details><summary>Evidence files</summary><ul>");
      if (files.isEmpty() && currentFiles.isEmpty()) html.append("<li>None</li>");
      for (String file : files) {
        String href = "../../../.." + "/regression/baselines/" + escapeHtml(baselineId) + "/checkpoints/" + escapeHtml(checkpointId) + "/" + escapeHtml(file);
        html.append("<li><a href='" + href + "'><code>").append(escapeHtml(file)).append("</code></a></li>");
      }
      for (String file : currentFiles) {
        String href = "checkpoints/" + checkpointId + "/" + file;
        if (!files.contains(file)) {
          html.append("<li><a href='" + href + "'><code>").append(escapeHtml(file)).append("</code></a></li>");
        }
      }
      html.append("</ul></details></section>");
    }
    html.append("</body></html>");
    Files.createDirectories(output.getParent());
    Files.writeString(output, html.toString());
  }

  private static String value(JsonObject object, String key) { return object.has(key) ? object.get(key).getAsString() : ""; }
  private static void collectDirectories(Path root, Set<String> names) throws IOException { if (Files.isDirectory(root)) try (var paths = Files.list(root)) { for (Path path : paths.filter(Files::isDirectory).toList()) names.add(path.getFileName().toString()); } }
  private static void collectFiles(Path root, Set<String> names) throws IOException { if (Files.isDirectory(root)) try (var paths = Files.list(root)) { for (Path path : paths.filter(Files::isRegularFile).toList()) names.add(path.getFileName().toString()); } }
  private static String renderImage(String label, Path path) throws IOException { return "<figure><figcaption>" + escapeHtml(label) + "</figcaption>" + (Files.isRegularFile(path) ? "<img alt='" + escapeHtml(label) + "' src='data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path)) + "'>" : "<div class='missing'>Not captured</div>") + "</figure>"; }
  private static String escapeHtml(String value) { return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace(String.valueOf((char) 34), "&quot;").replace("'", "&#39;"); }
}
