package regression;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VisualComparator {
  private record Rectangle(int x, int y, int width, int height) { boolean contains(int px, int py) { return px >= x && py >= y && px < x + width && py < y + height; } }

  public JsonObject compare(String checkpointId, Path baselinePath, Path currentPath, Path outputPath, double threshold, int colorTolerance) throws IOException {
    int tolerance = Math.max(0, Math.min(255, colorTolerance));
    if (!Files.isRegularFile(baselinePath)) return unavailable(checkpointId, "baseline_missing", threshold, tolerance);
    if (!Files.isRegularFile(currentPath)) return unavailable(checkpointId, "current_missing", threshold, tolerance);
    BufferedImage baseline = ImageIO.read(baselinePath.toFile());
    BufferedImage current = ImageIO.read(currentPath.toFile());
    if (baseline == null || current == null) return unavailable(checkpointId, "decode_error", threshold, tolerance);
    List<Rectangle> masks = loadMasks(baselinePath.getParent().resolve("visual-masks.json"));
    int width = Math.max(baseline.getWidth(), current.getWidth());
    int height = Math.max(baseline.getHeight(), current.getHeight());
    BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    int diffPixels = 0;
    int noisePixels = 0;
    int maskedPixels = 0;
    int dimensionPixels = 0;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        boolean baselineInBounds = x < baseline.getWidth() && y < baseline.getHeight();
        boolean currentInBounds = x < current.getWidth() && y < current.getHeight();
        if (!baselineInBounds || !currentInBounds) {
          dimensionPixels++;
          diffPixels++;
          diff.setRGB(x, y, new Color(255, 0, 64, 255).getRGB());
          continue;
        }
        int expected = baseline.getRGB(x, y);
        int observed = current.getRGB(x, y);
        if (masked(x, y, masks)) {
          maskedPixels++;
          diff.setRGB(x, y, new Color(80, 120, 255, 100).getRGB());
          continue;
        }
        Color expectedColor = new Color(expected, true);
        Color observedColor = new Color(observed, true);
        if (withinTolerance(expectedColor, observedColor, tolerance)) {
          if (expectedColor.getRed() != observedColor.getRed() || expectedColor.getGreen() != observedColor.getGreen() || expectedColor.getBlue() != observedColor.getBlue()) noisePixels++;
          int gray = (observedColor.getRed() + observedColor.getGreen() + observedColor.getBlue()) / 3;
          diff.setRGB(x, y, new Color(gray, gray, gray, 120).getRGB());
          continue;
        }
        diffPixels++;
        diff.setRGB(x, y, new Color(255, 0, 64, 255).getRGB());
      }
    }
    Files.createDirectories(outputPath.getParent());
    ImageIO.write(diff, "png", outputPath.toFile());
    int totalPixels = width * height;
    double changedRatio = totalPixels == 0 ? 0 : (double) diffPixels / totalPixels;
    JsonObject result = unavailable(checkpointId, "compared", threshold, tolerance);
    result.addProperty("baselineWidth", baseline.getWidth());
    result.addProperty("baselineHeight", baseline.getHeight());
    result.addProperty("currentWidth", current.getWidth());
    result.addProperty("currentHeight", current.getHeight());
    result.addProperty("diffPixels", diffPixels);
    result.addProperty("totalPixels", totalPixels);
    result.addProperty("changedRatio", changedRatio);
    result.addProperty("noisePixels", noisePixels);
    result.addProperty("maskedPixels", maskedPixels);
    result.addProperty("dimensionPixels", dimensionPixels);
    result.addProperty("passed", changedRatio <= threshold);
    result.addProperty("diffArtifact", "checkpoints/" + checkpointId + "/screenshot.diff.png");
    return result;
  }

  private boolean withinTolerance(Color expected, Color observed, int tolerance) {
    return Math.abs(expected.getRed() - observed.getRed()) <= tolerance
      && Math.abs(expected.getGreen() - observed.getGreen()) <= tolerance
      && Math.abs(expected.getBlue() - observed.getBlue()) <= tolerance;
  }

  private boolean masked(int x, int y, List<Rectangle> masks) { return masks.stream().anyMatch(mask -> mask.contains(x, y)); }

  private List<Rectangle> loadMasks(Path maskPath) throws IOException {
    List<Rectangle> masks = new ArrayList<>();
    if (!Files.isRegularFile(maskPath)) return masks;
    JsonObject root = JsonParser.parseString(Files.readString(maskPath)).getAsJsonObject();
    if (!root.has("rectangles") || !root.get("rectangles").isJsonArray()) return masks;
    for (var item : root.getAsJsonArray("rectangles")) {
      JsonObject rectangle = item.getAsJsonObject();
      int x = rectangle.get("x").getAsInt();
      int y = rectangle.get("y").getAsInt();
      int width = rectangle.get("width").getAsInt();
      int height = rectangle.get("height").getAsInt();
      if (width > 0 && height > 0) masks.add(new Rectangle(x, y, width, height));
    }
    return masks;
  }

  private JsonObject unavailable(String checkpointId, String status, double threshold, int colorTolerance) {
    JsonObject result = new JsonObject();
    result.addProperty("checkpointId", checkpointId);
    result.addProperty("status", status);
    result.addProperty("diffPixels", 0);
    result.addProperty("totalPixels", 0);
    result.addProperty("changedRatio", 0);
    result.addProperty("threshold", threshold);
    result.addProperty("colorTolerance", colorTolerance);
    result.addProperty("noisePixels", 0);
    result.addProperty("maskedPixels", 0);
    result.addProperty("dimensionPixels", 0);
    result.addProperty("passed", false);
    return result;
  }
}
