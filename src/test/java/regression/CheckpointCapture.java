package regression;

import com.codeborne.selenide.Selenide;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.codeborne.selenide.Selenide.executeJavaScript;

public final class CheckpointCapture {
  private static final String SEMANTIC_CAPABILITY_SCRIPT = """
(() => {
  const CAP = 120;
  const CONTROL_SELECTOR = 'button,a,input,select,textarea,[role],summary,[contenteditable="true"],details,main,nav,form,h1,h2,h3,h4,h5,h6,[aria-live],[role="status"]';
  const ROLE_BY_TAG = { A: 'link', BUTTON: 'button', SELECT: 'combobox', TEXTAREA: 'textbox', FORM: 'form', MAIN: 'main', NAV: 'navigation', SUMMARY: 'button', DETAILS: 'group' };
  const PAGE_BLOCK_ROLES = new Set(['heading', 'main', 'navigation', 'form', 'status']);
  const INTERACTIVE_ROLES = new Set(['button','link','checkbox','radio','textbox','combobox','listbox','menuitem','option','searchbox','slider','spinbutton','switch','tab']);
  function textById(id) { return (document.getElementById(id)?.textContent || '').trim(); }
  function norm(value) { return String(value || '').replace(/[\u0000-\u001f\u007f]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120); }
  function slug(value) { return norm(value).toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80) || 'unnamed'; }
  function explicitRole(element) { return norm(element.getAttribute('role')).toLowerCase().split(/\s+/)[0]; }
  function inferredRole(element) { const tag = element.tagName; if (/^H[1-6]$/.test(tag)) return 'heading'; if (tag === 'INPUT') { const type = String(element.getAttribute('type') || 'text').toLowerCase(); if (['button','submit','reset'].includes(type)) return 'button'; if (type === 'checkbox') return 'checkbox'; if (type === 'radio') return 'radio'; if (type === 'range') return 'slider'; if (type === 'number') return 'spinbutton'; if (type === 'search') return 'searchbox'; return 'textbox'; } if (element.hasAttribute('aria-live')) return 'status'; return ROLE_BY_TAG[tag] || ''; }
  function labelText(element) { const tag = element.tagName; const labelledBy = norm(element.getAttribute('aria-labelledby')).split(' ').filter(Boolean).map(textById).join(' '); const direct = element.getAttribute('aria-label') || labelledBy || element.getAttribute('alt') || element.getAttribute('title') || ''; if (direct) return norm(direct); if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') { const id = element.id ? document.querySelector('label[for="' + CSS.escape(element.id) + '"]')?.textContent : ''; const wrapping = element.closest('label')?.textContent || ''; const placeholder = element.getAttribute('placeholder') || ''; return norm(id || wrapping || placeholder || element.getAttribute('name') || ''); } if (/^H[1-6]$/.test(tag)) return norm(element.textContent || ''); return norm(element.innerText || element.textContent || ''); }
  function valueKind(element) { if (!['INPUT','TEXTAREA','SELECT'].includes(element.tagName)) return undefined; const type = String(element.getAttribute('type') || (element.tagName === 'SELECT' ? 'select' : 'text')).toLowerCase(); if (['password','hidden'].includes(type)) return 'redacted'; if (/token|secret|password|card|cvv|pin|otp/i.test([element.getAttribute('name'), element.getAttribute('id'), element.getAttribute('autocomplete'), element.getAttribute('aria-label')].join(' '))) return 'redacted'; if (['email','tel','url','number','search','date','checkbox','radio','select'].includes(type)) return type; return 'text'; }
  function isDisabled(element) { return element.hasAttribute('disabled') || element.getAttribute('aria-disabled') === 'true'; }
  function isVisible(element, rect) { const style = getComputedStyle(element); return !!(rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none' && Number(style.opacity || 1) !== 0); }
  function isInteractive(element, role, visible, disabled) { if (!visible || disabled) return false; if (INTERACTIVE_ROLES.has(role)) return true; return element.tabIndex >= 0 || element.hasAttribute('onclick') || element.isContentEditable; }
  function round(value) { return Math.round(Number(value) * 100) / 100; }
  const candidates = Array.from(document.querySelectorAll(CONTROL_SELECTOR)).slice(0, 400);
  const seen = new Set();
  const capabilities = [];
  for (const element of candidates) { const role = explicitRole(element) || inferredRole(element); if (!role || role === 'generic' || role === 'presentation' || role === 'none') continue; const name = labelText(element); const block = PAGE_BLOCK_ROLES.has(role); if (!name && !block) continue; const rect = element.getBoundingClientRect(); const visible = isVisible(element, rect); const disabled = isDisabled(element); const interactive = isInteractive(element, role, visible, disabled); if (!interactive && !block) continue; const accessibleName = block && role !== 'heading' && !element.getAttribute('aria-label') && !element.getAttribute('aria-labelledby') ? role : (block && !name ? role : name); const id = role + ':' + slug(accessibleName); if (seen.has(id)) continue; seen.add(id); const item = { id, role, accessibleName, visible, interactive, disabled, boundingBox: { x: round(rect.x), y: round(rect.y), width: round(rect.width), height: round(rect.height) } }; const kind = valueKind(element); if (kind) item.valueKind = kind; capabilities.push(item); if (capabilities.length >= CAP) break; }
  return capabilities.sort((left, right) => left.id.localeCompare(right.id) || left.role.localeCompare(right.role) || left.accessibleName.localeCompare(right.accessibleName));
})()
""";
  private CheckpointCapture() { }

  public static void capture(String checkpointId) {
    try {
      String runId = System.getenv().getOrDefault("TEST_RUN_ID", "local");
      AtomicEvidenceCapture capture = new AtomicEvidenceCapture(Path.of("reports", "runs", runId), checkpointId);
      capture.start();

      JsonObject dom = new JsonObject();
      dom.addProperty("url", com.codeborne.selenide.WebDriverRunner.url());
      dom.addProperty("title", Selenide.title());
      String pageSource = com.codeborne.selenide.WebDriverRunner.getWebDriver().getPageSource();
      dom.addProperty("html", redact(pageSource.substring(0, Math.min(pageSource.length(), 100_000))));

      String accessibilityJson = executeJavaScript("return JSON.stringify({controls:Array.from(document.querySelectorAll('button,a,input,select,textarea,[role]')).slice(0,200).map(e=>({role:e.getAttribute('role')||e.tagName.toLowerCase(),name:(e.getAttribute('aria-label')||e.getAttribute('alt')||e.textContent||'').trim().slice(0,300),disabled:e.hasAttribute('disabled')||e.getAttribute('aria-disabled')==='true'}))})");
      String geometryJson = executeJavaScript("return JSON.stringify({viewport:{width:innerWidth,height:innerHeight},controls:Array.from(document.querySelectorAll('button,a,input,select,textarea,[role]')).slice(0,200).map(e=>{const r=e.getBoundingClientRect();return {tag:e.tagName.toLowerCase(),x:r.x,y:r.y,width:r.width,height:r.height}})})");
      JsonObject accessibility = JsonParser.parseString(redact(accessibilityJson)).getAsJsonObject();
      JsonObject geometry = JsonParser.parseString(geometryJson).getAsJsonObject();
      JsonObject network = new JsonObject();
      network.add("results", new JsonArray());

      executeJavaScript("window.__sensitiveStyles=Array.from(document.querySelectorAll('input[type=password],input[name*=token i],input[name*=secret i],input[name*=card i]')).map(e=>[e,e.getAttribute('style')]);window.__sensitiveStyles.forEach(([e])=>e.style.cssText+=';color:transparent!important;background:#000!important;text-shadow:none!important')");
      String screenshotPath;
      try {
        screenshotPath = Selenide.screenshot("checkpoint_" + checkpointId.replace('.', '_'));
      } finally {
        executeJavaScript("(window.__sensitiveStyles||[]).forEach(([e,s])=>s===null?e.removeAttribute('style'):e.setAttribute('style',s));delete window.__sensitiveStyles");
      }
      if (screenshotPath == null) throw new IllegalStateException("Selenide did not produce a checkpoint screenshot");
      Path screenshotFile = screenshotPath.startsWith("file:")
        ? Path.of(java.net.URI.create(screenshotPath))
        : Path.of(screenshotPath);

      capture.captureJson("dom", dom);
      capture.captureBinary("screenshot", Files.readAllBytes(screenshotFile), "png");
      capture.captureJson("accessibility", accessibility);
      capture.captureJson("geometry", geometry);
      capture.captureJson("network", network);
      JsonObject snapshot = new JsonObject();
      snapshot.addProperty("target", com.codeborne.selenide.WebDriverRunner.url());
      snapshot.addProperty("semanticState", checkpointId);
      String capabilitiesJson = executeJavaScript("return JSON.stringify(" + SEMANTIC_CAPABILITY_SCRIPT + ")");
      snapshot.add("capabilities", JsonParser.parseString(capabilitiesJson).getAsJsonArray());
      capture.finalizeCapture(snapshot);
    } catch (Exception error) {
      throw new AssertionError("Checkpoint evidence capture failed for " + checkpointId, error);
    }
  }

  private static String redact(String value) {
    return value
      .replaceAll("(?i)([?&](?:token|access_token|api_key|password)=)[^&\"\s]+", "$1[REDACTED]")
      .replaceAll("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "[REDACTED:email]")
      .replaceAll("\\b(?:\\d[ -]*?){13,19}\\b", "[REDACTED:card]")
      .replaceAll("(?i)(bearer\\s+)[a-z0-9._~+\\/-]+", "$1[REDACTED]");
  }
}
