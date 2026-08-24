package steps;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import org.openqa.selenium.WebElement;

import static com.codeborne.selenide.Selenide.executeJavaScript;

/**
 * Browser-side discovery for the Corporate Actions tabs.
 *
 * The live admin SPA has rendered the tab labels outside ordinary top-level
 * Selenium traversal in some runs.  Keep discovery in one JavaScript probe so
 * the same recursive view is used for diagnostics, clicking, active-state
 * checks, and content checks.
 */
final class CorporateActionsTabProbe {
  private static final Gson GSON = new Gson();
  private static final String PROBE_SCRIPT = """
    return (() => {
      const mode = String(arguments[0] || 'diagnostic');
      const wanted = String(arguments[1] || '').trim();
      const wantedLower = wanted.toLowerCase();
      const canonicalLabels = ['Attachments', 'History', 'Signatures'];
      const knownLabels = Array.from(new Set([wanted, ...canonicalLabels]))
        .filter(Boolean)
        .map(value => String(value).trim());
      const normalize = value => String(value || '')
        .replace(/[\\u0000-\\u001f\\u007f]+/g, ' ')
        .replace(/\\s+/g, ' ')
        .trim();
      const redact = value => normalize(value)
        .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/gi, '[REDACTED:email]')
        .replace(/(password|token|secret|otp|authorization|cookie)\\s*[:=]\\s*[^,\\s<>"']+/gi, '$1=[REDACTED]');
      const truncate = (value, limit) => {
        const text = String(value || '');
        return text.length > limit ? text.slice(0, limit) + '…' : text;
      };
      const viewOf = element => {
        try { return element && element.ownerDocument && element.ownerDocument.defaultView || window; }
        catch (_) { return window; }
      };
      const visible = element => {
        if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;
        try {
          const style = viewOf(element).getComputedStyle(element);
          const box = element.getBoundingClientRect();
          return style.display !== 'none' && style.visibility !== 'hidden'
            && style.opacity !== '0' && box.width > 0 && box.height > 0;
        } catch (_) { return false; }
      };
      const disabled = element => element.hasAttribute('disabled')
        || element.getAttribute('aria-disabled') === 'true';
      const classText = element => normalize(typeof element.className === 'string'
        ? element.className : element.getAttribute('class') || '');
      const roleText = element => normalize(element.getAttribute('role')).toLowerCase();
      const pseudoContent = (element, pseudo) => {
        try {
          const raw = String(viewOf(element).getComputedStyle(element, pseudo).content || '');
          if (!raw || raw === 'none' || raw === 'normal') return '';
          if ((raw.startsWith('"') && raw.endsWith('"'))
              || (raw.startsWith("'") && raw.endsWith("'"))) return normalize(raw.slice(1, -1));
          return normalize(raw);
        } catch (_) { return ''; }
      };
      const directText = element => normalize(Array.from(element.childNodes || [])
        .filter(node => node.nodeType === Node.TEXT_NODE)
        .map(node => node.textContent || '').join(' '));
      const textContent = element => normalize(element.textContent || '');
      const innerText = element => normalize(element.innerText || '');
      const accessibleText = element => normalize([
        element.getAttribute('aria-label'),
        element.getAttribute('title'),
        element.getAttribute('alt'),
        element.getAttribute('data-label'),
        element.getAttribute('data-testid')
      ].filter(Boolean).join(' '));
      const parentOf = element => {
        if (!element) return null;
        if (element.parentElement) return element.parentElement;
        try {
          const root = element.getRootNode && element.getRootNode();
          return root && root.host || null;
        } catch (_) { return null; }
      };
      const tagName = element => String(element && element.tagName || '').toLowerCase();
      const isClickable = element => {
        if (!visible(element) || disabled(element)) return false;
        const tag = tagName(element);
        const role = roleText(element);
        const classes = classText(element);
        let styleCursor = '';
        try { styleCursor = viewOf(element).getComputedStyle(element).cursor || ''; } catch (_) { }
        return ['a', 'button', 'summary', 'input'].includes(tag)
          || ['button', 'tab', 'menuitem', 'option', 'link'].includes(role)
          || element.hasAttribute('onclick')
          || element.tabIndex >= 0
          || /(^|[-_ ])(tab|nav|link|menu-item)([-_ ]|$)/i.test(classes)
          || styleCursor === 'pointer';
      };
      const closestClickable = element => {
        let current = element;
        for (let depth = 0; current && depth < 12; depth += 1) {
          if (isClickable(current)) return current;
          current = parentOf(current);
        }
        return null;
      };
      const matchingText = (value, exactOnly = false) => {
        const normalized = normalize(value);
        if (!normalized || !wantedLower) return false;
        const lower = normalized.toLowerCase();
        return exactOnly ? lower === wantedLower
          : (lower === wantedLower || (normalized.length <= 320 && lower.includes(wantedLower)));
      };
      const matchesLabel = (value, label) => {
        const normalized = normalize(value).toLowerCase();
        const wantedLabel = normalize(label).toLowerCase();
        return !!normalized && !!wantedLabel
          && (normalized === wantedLabel || (normalized.length <= 320 && normalized.includes(wantedLabel)));
      };
      const safeOuterHTML = element => {
        try {
          const copy = element.cloneNode(true);
          const scrub = node => {
            if (!node || node.nodeType !== Node.ELEMENT_NODE) return;
            const tag = tagName(node);
            if (['input', 'textarea', 'select', 'option'].includes(tag)) {
              node.removeAttribute('value');
              node.removeAttribute('data-value');
              if (tag === 'textarea') node.textContent = '[REDACTED]';
              if (tag === 'option') node.textContent = '[REDACTED]';
            }
            Array.from(node.attributes || []).forEach(attribute => {
              if (/password|token|secret|otp|authorization|cookie|session/i.test(attribute.name)) {
                node.setAttribute(attribute.name, '[REDACTED]');
              }
              if (/^on(mouse|pointer|key|click|change|focus|blur)/i.test(attribute.name)) {
                node.removeAttribute(attribute.name);
              }
            });
            Array.from(node.children || []).forEach(scrub);
          };
          scrub(copy);
          return truncate(redact(copy.outerHTML || ''), 5000);
        } catch (_) { return '[outerHTML unavailable]'; }
      };
      const eventAttributeNames = element => {
        try {
          return Array.from(element && element.attributes || [])
            .map(attribute => String(attribute.name || ''))
            .filter(name => /^on(mouse|pointer|key|click|change|focus|blur|input|submit)/i.test(name));
        } catch (_) { return []; }
      };
      const round = value => Math.round(Number(value) * 100) / 100;
      const localRect = element => {
        try {
          const rect = element.getBoundingClientRect();
          return { x: round(rect.x), y: round(rect.y), width: round(rect.width), height: round(rect.height) };
        } catch (_) { return { x: 0, y: 0, width: 0, height: 0 }; }
      };
      const absoluteRect = element => {
        try {
          const local = element.getBoundingClientRect();
          let x = local.x;
          let y = local.y;
          let documentForElement = element.ownerDocument;
          let frameDepth = 0;
          while (documentForElement && documentForElement.defaultView
              && documentForElement.defaultView.frameElement && frameDepth < 12) {
            const frame = documentForElement.defaultView.frameElement;
            const frameRect = frame.getBoundingClientRect();
            x += frameRect.x;
            y += frameRect.y;
            documentForElement = frame.ownerDocument;
            frameDepth += 1;
          }
          return { x: round(x), y: round(y), width: round(local.width), height: round(local.height) };
        } catch (_) { return localRect(element); }
      };
      const describe = element => {
        const tag = tagName(element) || 'unknown';
        const role = roleText(element);
        const classes = truncate(classText(element), 180);
        return tag + (role ? '[role=' + role + ']' : '') + (classes ? '.' + classes.replace(/\\s+/g, '.') : '');
      };
      const belongsTo = (ancestor, descendant) => {
        let current = descendant;
        for (let depth = 0; current && depth < 24; depth += 1) {
          if (current === ancestor) return true;
          current = parentOf(current);
        }
        return false;
      };
      const activeEvidence = element => {
        let current = element;
        for (let depth = 0; current && depth < 12; depth += 1) {
          const ariaSelected = current.getAttribute('aria-selected');
          const ariaCurrent = current.getAttribute('aria-current');
          const dataState = current.getAttribute('data-state');
          const dataActive = current.getAttribute('data-active');
          const dataSelected = current.getAttribute('data-selected');
          const classes = classText(current);
          if (ariaSelected === 'true') return 'aria-selected=true';
          if (ariaCurrent === 'page' || ariaCurrent === 'true') return 'aria-current=' + ariaCurrent;
          if (/^(active|selected|true)$/i.test(dataState || '')) return 'data-state=' + dataState;
          if (/^true$/i.test(dataActive || '')) return 'data-active=true';
          if (/^true$/i.test(dataSelected || '')) return 'data-selected=true';
          if (/(^|[\\s_-])(active|selected|current|is-active|is-selected)([\\s_-]|$)/i.test(classes)) {
            return 'class=' + truncate(classes, 160);
          }
          const href = current.getAttribute('href');
          try {
            if (href && current.ownerDocument && current.ownerDocument.defaultView) {
              const link = new URL(href, current.ownerDocument.defaultView.location.href);
              if (link.pathname === current.ownerDocument.defaultView.location.pathname
                  && link.search === current.ownerDocument.defaultView.location.search
                  && link.hash === current.ownerDocument.defaultView.location.hash) return 'href=current-route';
            }
          } catch (_) { }
          current = parentOf(current);
        }
        return '';
      };
      const panelLike = element => {
        const role = roleText(element);
        const classes = classText(element);
        return role === 'tabpanel'
          || /(^|[-_ ])(tab-pane|tab-content|tab-body|tabpanel|content-panel)([-_ ]|$)/i.test(classes)
          || /mat-tab-body|ngb-nav-pane|p-tabpanel/i.test(classes);
      };
      const safePanelText = element => redact(truncate(innerText(element) || textContent(element), 3000));
      const scanned = new Set();
      const roots = new Set();
      const hits = [];
      const pseudoCandidates = [];
      const panelCandidates = [];
      const frameErrors = [];
      const scopeStats = [];
      let scannedElements = 0;
      let shadowRootCount = 0;
      let iframeDocumentCount = 0;
      const inspectElement = (element, root, scope) => {
        if (!element || scanned.has(element)) return;
        scanned.add(element);
        scannedElements += 1;
        const before = pseudoContent(element, '::before');
        const after = pseudoContent(element, '::after');
        const direct = directText(element);
        const content = textContent(element);
        const rendered = innerText(element);
        const accessible = accessibleText(element);
        const labelValues = [direct, content, rendered, accessible, before, after];
        const targetMatch = visible(element) && labelValues.some(value => matchingText(value));
        const anchorMatch = visible(element) && knownLabels.some(label => labelValues.some(value => matchesLabel(value, label)));
        const clickable = closestClickable(element);
        if (mode === 'diagnostic' && (before || after)) {
          if (/tab|nav|button|link/i.test(classText(element) + ' ' + roleText(element) + ' ' + tagName(element))) {
            pseudoCandidates.push({
              scope,
              element: describe(element),
              before: redact(before),
              after: redact(after),
              textContent: redact(content),
              innerText: redact(rendered),
              outerHTML: safeOuterHTML(element),
              boundingRect: { local: localRect(element), absolute: absoluteRect(element) }
            });
          }
        }
        if ((mode === 'state' || mode === 'diagnostic')
            && panelLike(element) && visible(element) && (rendered || content)) {
          panelCandidates.push({ element, root, scope, text: safePanelText(element) });
        }
        if (targetMatch || anchorMatch) {
          const reasons = [];
          if (matchingText(direct, true)) reasons.push('direct-text');
          if (matchingText(content, true)) reasons.push('textContent');
          if (matchingText(rendered, true)) reasons.push('innerText');
          if (matchingText(accessible, true)) reasons.push('accessible-name');
          if (matchingText(before, true)) reasons.push('::before');
          if (matchingText(after, true)) reasons.push('::after');
          const entry = {
            element,
            clickable,
            root,
            scope,
            targetMatch,
            anchorMatch,
            exact: reasons.length > 0,
            reasons,
            score: (targetMatch ? 100 : 0) + (reasons.length * 20)
              + (clickable === element ? 70 : clickable ? 35 : 0)
              + (/^(a|button|summary|input)$/i.test(tagName(element)) ? 30 : 0)
              + (/\\b(tab|tab-link|nav-link)\\b/i.test(classText(element)) ? 40 : 0)
              + (roleText(element) === 'tab' ? 50 : 0),
            before,
            after,
            direct,
            content,
            rendered,
            accessible
          };
          hits.push(entry);
        }
        if (element.shadowRoot) {
          shadowRootCount += 1;
          walk(element.shadowRoot, scope + ' > shadow:' + tagName(element), 0, 0);
        }
        if (element.tagName && /^(IFRAME|FRAME)$/i.test(element.tagName)) {
          try {
            const childDocument = element.contentDocument;
            if (childDocument) {
              iframeDocumentCount += 1;
              walk(childDocument, scope + ' > iframe#' + iframeDocumentCount, 0, 0);
            } else {
              frameErrors.push({ scope, element: describe(element), error: 'contentDocument=null' });
            }
          } catch (error) {
            frameErrors.push({ scope, element: describe(element), error: String(error && error.message || error) });
          }
        }
      };
      const walk = (root, scope, _offsetX, _offsetY) => {
        if (!root || roots.has(root)) return;
        roots.add(root);
        let elements = [];
        try { elements = Array.from(root.querySelectorAll('*')); }
        catch (error) {
          frameErrors.push({ scope, error: 'root-query-failed:' + String(error && error.message || error) });
          return;
        }
        scopeStats.push({ scope, elementCount: elements.length });
        elements.forEach(element => inspectElement(element, root, scope));
      };
      walk(document, 'document', 0, 0);
      hits.sort((left, right) => right.score - left.score);
      const best = hits.find(entry => entry.targetMatch && (entry.clickable || visible(entry.element)))
        || hits.find(entry => entry.targetMatch)
        || null;
      const prepare = entry => {
        if (!entry) return;
        try { (entry.clickable || entry.element).scrollIntoView({ block: 'center', inline: 'center' }); } catch (_) { }
        let currentDocument = (entry.clickable || entry.element).ownerDocument;
        for (let depth = 0; currentDocument && currentDocument.defaultView
            && currentDocument.defaultView.frameElement && depth < 12; depth += 1) {
          try { currentDocument.defaultView.frameElement.scrollIntoView({ block: 'center', inline: 'center' }); }
          catch (_) { }
          currentDocument = currentDocument.defaultView.frameElement.ownerDocument;
        }
      };
      const neighborLabels = entry => {
        if (!entry) return { element: null, labels: [], distinct: [], rect: null };
        const ancestors = [];
        let current = entry.clickable || entry.element;
        for (let depth = 0; current && depth < 12; depth += 1) {
          ancestors.push(current);
          current = parentOf(current);
        }
        let bestBar = null;
        let bestScore = -1;
        for (const ancestor of ancestors) {
          const labels = hits.filter(candidate => candidate.anchorMatch
            && candidate.root === entry.root
            && belongsTo(ancestor, candidate.element));
          const distinct = Array.from(new Set(labels.flatMap(candidate => knownLabels.filter(label =>
            [candidate.direct, candidate.content, candidate.rendered, candidate.accessible, candidate.before, candidate.after]
              .some(value => matchesLabel(value, label))))));
          let score = distinct.length * 100;
          if (roleText(ancestor) === 'tablist') score += 300;
          if (/tab|nav|menu|list/i.test(classText(ancestor) + ' ' + tagName(ancestor))) score += 100;
          const box = localRect(ancestor);
          if (box.width < 120 || box.height < 12) score -= 1000;
          if (distinct.length >= 2 && score > bestScore) {
            bestBar = { element: ancestor, labels, distinct, rect: absoluteRect(ancestor) };
            bestScore = score;
          }
        }
        if (!bestBar) {
          const fallback = ancestors.find(ancestor => /tab|nav|menu/i.test(classText(ancestor) + ' ' + tagName(ancestor)));
          if (fallback) bestBar = { element: fallback, labels: [], distinct: [], rect: absoluteRect(fallback) };
        }
        return bestBar || { element: null, labels: [], distinct: [], rect: null };
      };
      const activeFor = entry => {
        const evidence = activeEvidence(entry && (entry.clickable || entry.element));
        return { active: !!evidence, evidence };
      };
      const panelTextFor = entry => {
        if (!entry) return '';
        const active = entry.clickable || entry.element;
        const controlledIds = [];
        let tabBar = null;
        let current = active;
        for (let depth = 0; current && depth < 8; depth += 1) {
          const controls = normalize(current.getAttribute('aria-controls')).split(/\\s+/).filter(Boolean);
          controlledIds.push(...controls);
          if (!tabBar && tagName(current) === 'ul'
              && /(^|[\s_-])(tabs|tablist)([\s_-]|$)/i.test(classText(current) + ' ' + roleText(current))) {
            tabBar = current;
          }
          current = parentOf(current);
        }
        const controlled = panelCandidates.filter(panel => controlledIds.includes(panel.element.id));
        const sameRootPanels = panelCandidates.filter(panel => panel.root === entry.root);
        const rootDocument = entry.element && entry.element.ownerDocument;
        const mainCandidates = rootDocument ? Array.from(rootDocument.querySelectorAll('main,[role="main"]'))
          .filter(element => visible(element)).map(element => redact(truncate(innerText(element) || textContent(element), 3000))) : [];
        const contentRootCandidates = [];
        current = tabBar;
        for (let depth = 0; current && depth < 8; depth += 1) {
          if (tagName(current) === 'jhi-ca-application-form'
              || /application-form|form-management/i.test(classText(current) + ' ' + tagName(current))) {
            contentRootCandidates.push(current);
          }
          current = parentOf(current);
        }
        const activeContent = contentRootCandidates.map(root => {
          try {
            const copy = root.cloneNode(true);
            Array.from(copy.querySelectorAll('ul.tabs,[role="tablist"],.tabs'))
              .forEach(element => element.remove());
            return redact(truncate(copy.innerText || copy.textContent, 3000));
          } catch (_) { return ''; }
        });
        const selected = (controlled.length ? controlled : sameRootPanels).map(panel => panel.text)
          .concat(mainCandidates)
          .concat(activeContent);
        return Array.from(new Set(selected.filter(Boolean))).join(' | ');
      };
      const barFor = neighborLabels(best);
      const targetElement = best && (best.clickable || best.element) || null;
      if (mode === 'element') return targetElement;
      if (mode === 'prepare') {
        prepare(best);
        return JSON.stringify({ prepared: !!best, target: targetElement ? describe(targetElement) : null });
      }
      if (mode === 'state') {
        const active = activeFor(best);
        return JSON.stringify({
          wanted,
          matched: !!best,
          candidateCount: hits.filter(entry => entry.targetMatch).length,
          active: active.active,
          activeEvidence: active.evidence,
          panelText: panelTextFor(best),
          target: targetElement ? describe(targetElement) : null,
          anchorLabels: barFor.distinct,
          frameErrors
        });
      }
      if (mode === 'geometry') {
        prepare(best);
        const targetRect = targetElement ? absoluteRect(targetElement) : null;
        const barRect = barFor.element ? absoluteRect(barFor.element) : null;
        const neighbors = barFor.labels.slice(0, 20).map(label => ({
          label: redact([label.direct, label.content, label.rendered, label.accessible, label.before, label.after].find(value => value) || ''),
          rect: absoluteRect(label.clickable || label.element)
        }));
        return JSON.stringify({
          wanted,
          target: targetRect,
          targetDescription: targetElement ? describe(targetElement) : null,
          anchorBar: barRect,
          anchorLabels: barFor.distinct,
          neighbors,
          viewport: { width: window.innerWidth, height: window.innerHeight, devicePixelRatio: window.devicePixelRatio },
          matched: !!best,
          frameErrors
        });
      }
      const diagnosticHits = hits.slice(0, 160).map(entry => ({
        scope: entry.scope,
        targetMatch: entry.targetMatch,
        anchorMatch: entry.anchorMatch,
        score: entry.score,
        reasons: entry.reasons,
        element: describe(entry.element),
        clickable: entry.clickable ? describe(entry.clickable) : null,
        directText: redact(entry.direct),
        textContent: redact(truncate(entry.content, 600)),
        innerText: redact(truncate(entry.rendered, 600)),
        before: redact(entry.before),
        after: redact(entry.after),
        accessibleText: redact(entry.accessible),
        outerHTML: safeOuterHTML(entry.element),
        eventAttributes: eventAttributeNames(entry.element),
        clickableOuterHTML: entry.clickable && entry.clickable !== entry.element ? safeOuterHTML(entry.clickable) : null,
        clickableEventAttributes: entry.clickable ? eventAttributeNames(entry.clickable) : [],
        parentOuterHTML: parentOf(entry.element) ? safeOuterHTML(parentOf(entry.element)) : null,
        parentEventAttributes: parentOf(entry.element) ? eventAttributeNames(parentOf(entry.element)) : [],
        boundingRect: { local: localRect(entry.element), absolute: absoluteRect(entry.element) },
        clickableBoundingRect: entry.clickable ? { local: localRect(entry.clickable), absolute: absoluteRect(entry.clickable) } : null
      }));
      return JSON.stringify({
        url: redact(location.href),
        title: redact(document.title),
        readyState: document.readyState,
        wanted,
        scannedElements,
        roots: roots.size,
        shadowRootCount,
        iframeDocumentCount,
        scopeStats,
        frameErrors,
        candidateCount: hits.filter(entry => entry.targetMatch).length,
        best: best ? {
          element: describe(best.element),
          clickable: targetElement ? describe(targetElement) : null,
          reasons: best.reasons,
          anchorLabels: barFor.distinct,
          boundingRect: targetElement ? { local: localRect(targetElement), absolute: absoluteRect(targetElement) } : null
        } : null,
        pseudoCandidates: pseudoCandidates.slice(0, 160),
        hits: diagnosticHits
      });
    })(arguments[0], arguments[1])
    """;

  private CorporateActionsTabProbe() { }

  static WebElement findClickable(String tabName) {
    Object result = executeJavaScript(PROBE_SCRIPT, "element", tabName);
    return result instanceof WebElement ? (WebElement) result : null;
  }

  static void prepare(String tabName) {
    executeJavaScript(PROBE_SCRIPT, "prepare", tabName);
  }

  static String geometry(String tabName) {
    Object result = executeJavaScript(PROBE_SCRIPT, "geometry", tabName);
    return jsonString(result);
  }

  static String diagnostic(String tabName) {
    Object result = executeJavaScript(PROBE_SCRIPT, "diagnostic", tabName);
    return jsonString(result);
  }

  static JsonObject state(String tabName) {
    Object result = executeJavaScript(PROBE_SCRIPT, "state", tabName);
    return JsonParser.parseString(jsonString(result)).getAsJsonObject();
  }

  private static String jsonString(Object result) {
    if (result == null) return "{}";
    if (result instanceof String string) return string;
    return GSON.toJson(result);
  }

  static boolean isActive(String tabName) {
    JsonObject state = state(tabName);
    return state.has("active") && state.get("active").getAsBoolean();
  }

  static String panelText(String tabName) {
    JsonObject state = state(tabName);
    return state.has("panelText") ? state.get("panelText").getAsString() : "";
  }
}
