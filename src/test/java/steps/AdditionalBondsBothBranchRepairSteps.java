package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.When;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.Select;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Keeps Additional issuance of Bonds on the observed Both paid-up branch. */
public final class AdditionalBondsBothBranchRepairSteps {
  private static final String TYPE = "Additional issuance of Bonds";

  private final DisposableDividendSteps flow;

  public AdditionalBondsBothBranchRepairSteps(DisposableDividendSteps flow,
                                               DisposableExecutionRepairSteps repair) {
    this.flow = flow;
  }

  @When("I fill and safely save the disposable Additional issuance of Bonds form on the Both paid-up branch")
  public void fillAndSaveBothBranch() throws Exception {
    flow.setAppType(TYPE);
    // This application adds to an existing bond issue. Prefer the observed
    // bond source. A non-bond instrument is not valid for this workflow.
    selectObservedSourceInstrument();

    Throwable initialFailure = null;
    long previousTimeout = Configuration.timeout;
    try {
      // Never let one stale/click-intercepted generated helper consume 70 sec.
      Configuration.timeout = Math.min(previousTimeout, 12000);
      flow.fillDisposableApplicationAndSaveDraft(TYPE);
      if (savedDetailVisible()) return;
      initialFailure = new AssertionError("AIB Save as Draft returned without opening the application detail");
      System.out.println("AIB_BOTH_REPAIR root=IncompleteSaveTransition");
    } catch (Throwable failure) {
      initialFailure = failure;
      if (!isRepairableAibFailure(failure)) rethrow(failure);
      System.out.println("AIB_BOTH_REPAIR root=" + rootCause(failure).getClass().getSimpleName());
    } finally {
      Configuration.timeout = previousTimeout;
    }

    ensureObservedBothBranch();
    setObservedMinimumAdditionalNominalValue();
    setObservedPaidAndUnpaidSplit();
    setObservedEffectiveDate();
    setObservedIssuerContact();
    resolveObservedBondholderRow();
    fastSaveDraft(initialFailure);
  }

  private static void selectObservedSourceInstrument() {
    SelenideElement source = $("#aib_security_name");
    long visibleDeadline = System.currentTimeMillis() + 6000;
    while (System.currentTimeMillis() < visibleDeadline
        && (!source.exists() || !source.isDisplayed() || !source.isEnabled())) sleep(100);
    if (!source.exists() || !source.isDisplayed() || !source.isEnabled()) {
      throw new AssertionError("AIB form did not expose an enabled source instrument control");
    }

    Select select = new Select(source.getWrappedElement());
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    int target = -1;
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getAttribute("textContent")).trim();
      if (!option.isEnabled() || value.isBlank() || "null".equalsIgnoreCase(value)) continue;
      if (label.toLowerCase(Locale.ROOT).contains("select")) continue;
      if (label.toLowerCase(Locale.ROOT).contains("bond")) { target = index; break; }
    }
    if (target < 0) {
      throw new AssertionError("AIB source instrument control exposed no selectable bond instrument");
    }

    org.openqa.selenium.WebElement currentOption = select.getFirstSelectedOption();
    String currentValue = safe(currentOption.getAttribute("value")).trim();
    String currentLabel = safe(currentOption.getAttribute("textContent")).toLowerCase(Locale.ROOT);
    if (currentValue.isBlank() || "null".equalsIgnoreCase(currentValue)
        || !currentLabel.contains("bond")) {
      select.selectByIndex(target);
      dispatch(source);
      long deadline = System.currentTimeMillis() + 6000;
      while (System.currentTimeMillis() < deadline) {
        SelenideElement paidField = $("#aib_nominal_value_paid");
        SelenideElement unpaidField = $("#aib_nominal_value_unpaid");
        if (!paidField.exists() || !unpaidField.exists()) {
          sleep(100);
          continue;
        }
        String paid = numericValue(safe(paidField.getValue()));
        String unpaid = numericValue(safe(unpaidField.getValue()));
        if (!paid.isBlank() || !unpaid.isBlank()) break;
        sleep(100);
      }
    }
    System.out.println("AIB_SOURCE_INSTRUMENT selected_bond=true");
  }

  private static void setObservedMinimumAdditionalNominalValue() {
    SelenideElement field = $("#aib_additional_nominal_value");
    if (!field.exists() || !field.isDisplayed() || !field.isEnabled()) {
      throw new AssertionError("AIB Both branch did not expose #aib_additional_nominal_value");
    }
    enterObservedValue(field, "2001");
    double observed = numericDouble(safe(field.getValue()));
    if (observed < 2001.0) {
      throw new AssertionError("AIB additional nominal value did not retain the validated minimum");
    }
    long deadline = System.currentTimeMillis() + 3000;
    while (System.currentTimeMillis() < deadline) {
      String body = safe($("body").getText());
      if (!body.contains("This field should be at least 2000")) break;
      field.click();
      field.pressTab();
      sleep(100);
    }
    if (safe($("body").getText()).contains("This field should be at least 2000")) {
      throw new AssertionError("AIB Angular validation still rejects the validated additional nominal value");
    }
    System.out.println("AIB_ADDITIONAL_NOMINAL_VALUE retained=true");
  }

  private static void setObservedPaidAndUnpaidSplit() {
    SelenideElement paid = $("#aib_nominal_value_paid");
    SelenideElement unpaid = $("#aib_nominal_value_unpaid");
    if (!paid.exists() || !unpaid.exists() || !paid.isDisplayed() || !unpaid.isDisplayed()) {
      throw new AssertionError("AIB Both branch did not expose paid and unpaid nominal controls");
    }
    enterObservedValue(paid, "2000");
    enterObservedValue(unpaid, "1");
    if (Math.abs(numericDouble(safe(paid.getValue())) + numericDouble(safe(unpaid.getValue())) - 2001.0) > 0.0001) {
      throw new AssertionError("AIB Both split does not equal the additional nominal value");
    }
    System.out.println("AIB_BOTH_SPLIT valid=true");
  }

  private static void setObservedEffectiveDate() {
    LocalDate date = LocalDate.now().plusDays(1);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      date = date.plusDays(1);
    }
    SelenideElement field = $("#aib_effective_date");
    if (!field.exists() || !field.isDisplayed() || !field.isEnabled()) {
      throw new AssertionError("AIB form did not expose the required effective-date control");
    }
    enterObservedValue(field, date.toString());
    if (!date.toString().equals(safe(field.getValue()))) {
      throw new AssertionError("AIB effective date did not retain the configured business date");
    }
  }

  private static void setObservedIssuerContact() {
    SelenideElement name = $("#aib_issuer_contact_person_name");
    SelenideElement email = $("#aib_issuer_email");
    if (name.exists() && name.isDisplayed() && name.isEnabled() && safe(name.getValue()).isBlank()) {
      enterObservedValue(name, "Autotest Contact");
    }
    if (email.exists() && email.isDisplayed() && email.isEnabled() && safe(email.getValue()).isBlank()) {
      enterObservedValue(email, "autotest@example.com");
    }
  }

  private static void resolveObservedBondholderRow() {
    ensureObservedBothBranch();

    String holderCode = safe($("#aib_issuer_reg_number").getValue()).trim();
    if (holderCode.isBlank()) {
      holderCode = Long.toString(100000000L + Math.floorMod(System.nanoTime(), 900000000L));
    }

    double paid = numericDouble(safe($("#aib_nominal_value_paid").getValue()));
    double unpaid = numericDouble(safe($("#aib_nominal_value_unpaid").getValue()));
    if (paid <= 0.0001 || unpaid <= 0.0001) {
      throw new AssertionError("AIB Both branch requires positive paid and unpaid nominal values");
    }

    String distributionHolderCode = findObservedDistributionHolderCode(holderCode);
    // Source changes performed during Distribution Account discovery can reset
    // dependent nominal fields. Re-assert the business branch on the selected
    // source that actually exposed the required account.
    ensureObservedBothBranch();
    setObservedMinimumAdditionalNominalValue();
    setObservedPaidAndUnpaidSplit();
    setObservedEffectiveDate();
    setObservedIssuerContact();
    paid = numericDouble(safe($("#aib_nominal_value_paid").getValue()));
    unpaid = numericDouble(safe($("#aib_nominal_value_unpaid").getValue()));
    populateObservedBondholderRow(0, distributionHolderCode, true, unpaid);
    ensureSecondBondholderRow();
    populateObservedBondholderRow(1, holderCode, false, paid);
    System.out.println("AIB_DISTRIBUTION_SPLIT valid=true");
  }

  private static void populateObservedBondholderRow(int index, String holderCode,
                                                     boolean distribution, double amountValue) {
    SelenideElement row = $("#aib_bondholders_table_row_" + index);
    SelenideElement code = $("#aib_bht_code_" + index);
    SelenideElement account = $("#aib_bht_account_" + index);
    SelenideElement name = $("#aib_bht_name_" + index);
    SelenideElement amount = $("#aib_bht_amount_of_bonds_issued_" + index);
    if (!row.exists() || !code.exists() || !account.exists() || !name.exists() || !amount.exists()) {
      throw new AssertionError("AIB Both branch did not expose bondholder row " + index);
    }

    setAndDispatch(code, holderCode);
    if (!hasSelectableOption(account) || !hasSelectableOption(name)) {
      SelenideElement search = row.$("button.button-search");
      if (!search.exists() || !search.isDisplayed() || !search.isEnabled()) {
        throw new AssertionError("AIB row " + index + " did not expose the bondholder lookup button");
      }
      executeJavaScript("arguments[0].scrollIntoView({block:'center',inline:'center'}); arguments[0].click();",
        search.getWrappedElement());
      long deadline = System.currentTimeMillis() + 8000;
      while (System.currentTimeMillis() < deadline
          && (!hasSelectableOption(account) || !hasSelectableOption(name))) sleep(100);
    }
    if (!hasSelectableOption(account) || !hasSelectableOption(name)) {
      throw new AssertionError("AIB row " + index + " lookup returned no account/name options");
    }

    Select accountSelect = new Select(account.getWrappedElement());
    int accountIndex = distribution
      ? distributionAccountIndex(accountSelect)
      : firstNonDistributionOptionIndex(accountSelect);
    if (accountIndex < 0) {
      throw new AssertionError("AIB row " + index + " has no "
        + (distribution ? "Distribution Account" : "paid-security account"));
    }
    selectObservedOption(account, accountIndex);
    selectFirstNonEmpty(name);
    setAndDispatch(amount, decimal(amountValue));
  }

  private static String findObservedDistributionHolderCode(String issuerCode) {
    if (!$("#aib_bondholders_table_row_0").exists()
        || !$("#aib_bht_code_0").exists()
        || !$("#aib_bht_account_0").exists()) {
      throw new AssertionError("AIB form did not expose the first distribution row");
    }

    List<String> candidates = observedRegistryCodeCandidates(issuerCode);
    SelenideElement source = $("#aib_security_name");
    List<String> sourceValues = new ArrayList<>();
    String initialSourceValue = "";
    if (source.exists()) {
      Select initialSource = new Select(source.getWrappedElement());
      String selectedValue = safe(initialSource.getFirstSelectedOption().getAttribute("value")).trim();
      initialSourceValue = selectedValue;
      if (!selectedValue.isBlank() && !"null".equalsIgnoreCase(selectedValue)) sourceValues.add(selectedValue);
    }

    installAibLookupProbe();
    int sourceCandidateIndex = 0;
    for (String sourceValue : sourceValues) {
      sourceCandidateIndex++;
      source = $("#aib_security_name");
      if (!source.exists() || !source.isDisplayed() || !source.isEnabled()) continue;
      Select sourceSelect = new Select(source.getWrappedElement());
      boolean available = sourceSelect.getOptions().stream().anyMatch(option ->
        sourceValue.equals(safe(option.getAttribute("value")).trim()) && option.isEnabled());
      if (!available) continue;
      String currentValue = safe(sourceSelect.getFirstSelectedOption().getAttribute("value")).trim();
      if (!sourceValue.equals(currentValue)) {
        sourceSelect.selectByValue(sourceValue);
        dispatch(source);
        long rerenderDeadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < rerenderDeadline
            && !$("#aib_bondholders_table_row_0").exists()) sleep(100);
      }
      System.out.println("AIB_DISTRIBUTION_SOURCE_CANDIDATE index=" + sourceCandidateIndex);

      for (String candidate : candidates) {
        // Changing the source security rerenders the dependent bondholder table.
        // Always reacquire its controls instead of retaining stale WebElements.
        SelenideElement row = $("#aib_bondholders_table_row_0");
        SelenideElement code = $("#aib_bht_code_0");
        SelenideElement account = $("#aib_bht_account_0");
        if (!row.exists() || !code.exists() || !account.exists()) continue;
        setAndDispatch(code, candidate);
        SelenideElement search = $("#aib_bondholders_table_row_0 button.button-search");
        if (!search.exists() || !search.isDisplayed() || !search.isEnabled()) continue;
        int completionCount = aibLookupCompletionCount();
        executeJavaScript("arguments[0].scrollIntoView({block:'center'});"
            + "window.__aibLookupCandidate=String(arguments[1]);window.__aibLookupArmed=true;"
            + "try{arguments[0].click()}finally{window.__aibLookupArmed=false}",
          search.getWrappedElement(), candidate);
        long deadline = System.currentTimeMillis() + 3500;
        while (System.currentTimeMillis() < deadline) {
          Boolean lookupSucceeded = aibLookupSucceededAfter(completionCount);
          if (lookupSucceeded != null) {
            if (lookupSucceeded
                && candidate.equals(safe($("#aib_bht_code_0").getValue()).trim())
                && liveSelectHasOptionContaining("aib_bht_account_0", "distribution account")) {
              return candidate;
            }
            break;
          }
          sleep(100);
        }
      }
    }
    restoreAibSource(initialSourceValue);
    throw new AssertionError("AIB authenticated entity data exposed no holder with a Distribution Account"
      + " across " + candidates.size() + " observed registry-code candidates");
  }

  private static void installAibLookupProbe() {
    executeJavaScript(
      "if(!window.__aibLookupProbeInstalled){window.__aibLookupProbeInstalled=true;"
        + "window.__aibLookupArmed=false;window.__aibLookupCandidate='';window.__aibLookupCompletions=[];"
        + "const open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;"
        + "XMLHttpRequest.prototype.open=function(method,url){this.__aibLookupUrl=String(url);return open.apply(this,arguments)};"
        + "XMLHttpRequest.prototype.send=function(body){if(window.__aibLookupArmed){"
        + "const candidate=String(window.__aibLookupCandidate||''),request=String(this.__aibLookupUrl||'')+' '+String(body||'');"
        + "if(candidate&&request.includes(candidate)){this.addEventListener('loadend',()=>"
        + "window.__aibLookupCompletions.push({status:this.status,matched:true}));}}"
        + "return send.apply(this,arguments)};}"
    );
  }

  private static int aibLookupCompletionCount() {
    Object count = executeJavaScript("return (window.__aibLookupCompletions||[]).length;");
    return count instanceof Number number ? number.intValue() : 0;
  }

  private static Boolean aibLookupSucceededAfter(int previousCount) {
    Object result = executeJavaScript(
      "const values=(window.__aibLookupCompletions||[]).slice(arguments[0]);"
        + "return values.length?values.some(value=>value.matched&&value.status>=200&&value.status<300):null;",
      previousCount);
    return result instanceof Boolean value ? value : null;
  }

  private static void restoreAibSource(String sourceValue) {
    if (sourceValue == null || sourceValue.isBlank() || "null".equalsIgnoreCase(sourceValue)) return;
    SelenideElement source = $("#aib_security_name");
    if (!source.exists() || !source.isDisplayed() || !source.isEnabled()) return;
    Select select = new Select(source.getWrappedElement());
    String current = safe(select.getFirstSelectedOption().getAttribute("value")).trim();
    if (!sourceValue.equals(current)) {
      select.selectByValue(sourceValue);
      dispatch(source);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> observedRegistryCodeCandidates(String issuerCode) {
    Object observed = executeJavaScript(
      "const out=new Set(),add=v=>{v=String(v??'').trim();if(/^\\d{8,14}$/.test(v))out.add(v)};"
        + "add(arguments[0]);"
        + "const root=document.querySelector('#aib_bondholders_table_row_0')?.closest('form')||document.querySelector('form');"
        + "for(const e of (root?root.querySelectorAll('input,select,option'):[])){"
        + "const key=((e.id||'')+' '+(e.name||'')).toLowerCase();"
        + "if(/(reg|registry|registration).*(code|number)|reg_number/.test(key))add(e.value);"
        + "}return [...out];",
      issuerCode);
    List<String> result = new ArrayList<>();
    if (observed instanceof List<?> values) {
      for (Object value : values) {
        String candidate = safe(String.valueOf(value)).trim();
        if (candidate.matches("\\d{8,14}") && !result.contains(candidate)) result.add(candidate);
      }
    }
    if (!result.contains(issuerCode)) result.add(0, issuerCode);
    return result;
  }

  private static void ensureSecondBondholderRow() {
    if ($("#aib_bondholders_table_row_1").exists()) return;
    for (SelenideElement button : $$("button")) {
      if (!button.isDisplayed() || !button.isEnabled()) continue;
      if (!"Add row".equalsIgnoreCase(safe(button.getText()).replaceAll("\\s+", " ").trim())) continue;
      executeJavaScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();",
        button.getWrappedElement());
      long deadline = System.currentTimeMillis() + 3000;
      while (System.currentTimeMillis() < deadline && !$("#aib_bondholders_table_row_1").exists()) sleep(100);
      break;
    }
    if (!$("#aib_bondholders_table_row_1").exists()) {
      throw new AssertionError("AIB Both branch could not add the paid-security distribution row");
    }
  }

  private static void ensureObservedBothBranch() {
    SelenideElement both = $("input[type=radio][name='aib_paid_up'][value='2']");
    if (!both.exists()) both = $("input[type=radio][name='aib_paid_up'][id*='both']");
    if (!both.exists() || !both.isDisplayed() || !both.isEnabled()) {
      throw new AssertionError("AIB form did not expose the observed Both paid-up branch");
    }
    if (!both.isSelected()) {
      executeJavaScript("arguments[0].click();", both.getWrappedElement());
      dispatch(both);
      long deadline = System.currentTimeMillis() + 3000;
      while (System.currentTimeMillis() < deadline && !both.isSelected()) sleep(100);
    }
    if (!both.isSelected()) throw new AssertionError("AIB Both paid-up branch did not remain selected");
  }

  private static void fastSaveDraft(Throwable initialFailure) {
    installAibSaveResponseProbe();
    for (int attempt = 1; attempt <= 2; attempt++) {
      clickStickySaveDraft();
      long deadline = System.currentTimeMillis() + 10000;
      while (System.currentTimeMillis() < deadline) {
        if (savedDetailVisible()) return;
        sleep(100);
      }

      int validationCount = visibleValidationMessageCount();
      if (validationCount > 0) {
        throw new AssertionError("AIB Save as Draft rejected by visible validation; count=" + validationCount,
          initialFailure);
      }
      System.out.println("AIB_SAVE_RETRY attempt=" + attempt);
    }
    throw new AssertionError("AIB Save as Draft did not reach an application detail within 20s"
      + "; visibleValidationCount=" + visibleValidationMessageCount() + "; state=" + repairState()
      + "; apiResponses=" + aibSaveResponses(), initialFailure);
  }

  private static void installAibSaveResponseProbe() {
    executeJavaScript(
      "if(!window.__aibSaveProbeInstalled){window.__aibSaveProbeInstalled=true;window.__aibSaveResponses=[];"
        + "const open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;"
        + "XMLHttpRequest.prototype.open=function(method,url){this.__aibSaveUrl=String(url);return open.apply(this,arguments)};"
        + "XMLHttpRequest.prototype.send=function(){if((this.__aibSaveUrl||'').includes('corporate-actions')){"
        + "this.addEventListener('loadend',()=>window.__aibSaveResponses.push({"
        + "status:this.status,responseBytes:String(this.responseText||'').length}));}return send.apply(this,arguments)};}"
    );
  }

  private static String aibSaveResponses() {
    Object responses = executeJavaScript("return JSON.stringify((window.__aibSaveResponses||[]).slice(-5));");
    return responses == null ? "[]" : responses.toString();
  }

  private static void clickStickySaveDraft() {
    List<SelenideElement> candidates = new ArrayList<>();
    for (SelenideElement control : $$("button, a, [role=button]")) {
      String label = safe(control.getText()).replaceAll("\\s+", " ").trim();
      if (control.isDisplayed() && control.isEnabled() && "Save as Draft".equalsIgnoreCase(label)) {
        candidates.add(control);
      }
    }
    if (candidates.isEmpty()) throw new AssertionError("AIB editing surface exposed no enabled Save as Draft control");
    for (int index = candidates.size() - 1; index >= 0; index--) {
      SelenideElement control = candidates.get(index);
      executeJavaScript("arguments[0].scrollIntoView({block:'center',inline:'center'});", control.getWrappedElement());
      executeJavaScript("arguments[0].click();", control.getWrappedElement());
      return;
    }
  }

  private static boolean savedDetailVisible() {
    try {
      String current = url();
      if (current != null && !current.contains("/country/") && !current.matches(".*/new(?:[?#].*)?$")
          && current.matches(".*/corporate-actions/application-form/\\d+(?:[/?#].*)?")) return true;
      String body = $("body").getText();
      return body != null && body.contains("Sign Document") && body.contains(TYPE);
    } catch (Throwable ignored) { return false; }
  }

  private static int visibleValidationMessageCount() {
    Object value = executeJavaScript(
      "const sel='.invalid-feedback,.alert-danger,.text-danger,[role=alert]';"
        + "return [...document.querySelectorAll(sel)].filter(e=>e.offsetParent!==null"
        + "&&String(e.innerText||'').trim()).length;");
    return value instanceof Number ? ((Number) value).intValue() : 0;
  }

  private static String repairState() {
    Object value = executeJavaScript(
      "const ids=['aib_additional_nominal_value','aib_nominal_value_paid','aib_nominal_value_unpaid',"
        + "'aib_bht_account_0','aib_bht_name_0','aib_bht_amount_of_bonds_issued_0'];"
        + "const fields=ids.map(id=>document.getElementById(id));"
        + "const invalid=[...document.querySelectorAll('input,select,textarea')].filter(e=>e.offsetParent!==null"
        + "&&(e.matches(':invalid')||e.classList.contains('ng-invalid')));"
        + "return JSON.stringify({requiredPresent:fields.filter(Boolean).length,requiredInvalid:fields.filter(e=>e"
        + "&&(e.matches(':invalid')||e.classList.contains('ng-invalid'))).length,"
        + "visibleInvalidCount:invalid.length,formCount:document.querySelectorAll('form').length});");
    return value == null ? "" : value.toString();
  }

  private static boolean hasSelectableOption(SelenideElement field) {
    try {
      if (!field.exists() || !"select".equalsIgnoreCase(field.getTagName())) return false;
      return firstNonEmptyOptionIndex(new Select(field.getWrappedElement())) >= 0;
    } catch (Throwable ignored) { return false; }
  }

  private static int firstNonEmptyOptionIndex(Select select) {
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim();
      if (option.isEnabled() && !value.isBlank() && !"null".equalsIgnoreCase(value)
          && !label.toLowerCase(Locale.ROOT).contains("select")) return index;
    }
    return -1;
  }

  private static int optionIndexContaining(Select select, String needle) {
    String wanted = needle.toLowerCase(Locale.ROOT);
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim().toLowerCase(Locale.ROOT);
      if (option.isEnabled() && !value.isBlank() && !"null".equalsIgnoreCase(value) && label.contains(wanted)) return index;
    }
    return -1;
  }

  private static boolean liveSelectHasOptionContaining(String id, String needle) {
    Object result = executeJavaScript(
      "const el=document.getElementById(arguments[0]),wanted=String(arguments[1]).toLowerCase();"
        + "if(!el)return false;return [...el.options].some(o=>!o.disabled&&String(o.value||'').trim()"
        + "&&String(o.value).toLowerCase()!=='null'&&String(o.textContent||'').toLowerCase().includes(wanted));",
      id, needle);
    return Boolean.TRUE.equals(result);
  }

  private static int firstNonDistributionOptionIndex(Select select) {
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim().toLowerCase(Locale.ROOT);
      if (option.isEnabled() && !value.isBlank() && !"null".equalsIgnoreCase(value)
          && !label.contains("select") && !label.contains("distribution account")) return index;
    }
    return -1;
  }

  private static int distributionAccountIndex(Select select) {
    return optionIndexContaining(select, "distribution account");
  }

  private static String optionInventory(Select select) {
    List<String> result = new ArrayList<>();
    for (var option : select.getOptions()) result.add(safe(option.getText()).trim());
    return result.toString();
  }

  private static void selectFirstNonEmpty(SelenideElement field) {
    Select select = new Select(field.getWrappedElement());
    int index = firstNonEmptyOptionIndex(select);
    if (index < 0) throw new AssertionError("AIB native select exposed no non-empty option: " + safe(field.getAttribute("id")));
    selectObservedOption(field, index);
  }

  private static void selectObservedOption(SelenideElement field, int index) {
    executeJavaScript("arguments[0].scrollIntoView({block:'center',inline:'center'});", field.getWrappedElement());
    executeJavaScript(
      "const el=arguments[0],index=arguments[1];el.selectedIndex=index;"
        + "el.dispatchEvent(new Event('input',{bubbles:true}));"
        + "el.dispatchEvent(new Event('change',{bubbles:true}));"
        + "el.dispatchEvent(new Event('blur',{bubbles:true}));",
      field.getWrappedElement(), index);
    sleep(150);
  }

  private static void enterObservedValue(SelenideElement field, String value) {
    executeJavaScript("arguments[0].scrollIntoView({block:'center',inline:'center'});", field.getWrappedElement());
    if ("date".equalsIgnoreCase(safe(field.getAttribute("type")))) {
      executeJavaScript(
        "const el=arguments[0],value=arguments[1];"
          + "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
          + "setter.call(el,value);el.dispatchEvent(new Event('input',{bubbles:true}));"
          + "el.dispatchEvent(new Event('change',{bubbles:true}));el.dispatchEvent(new Event('blur',{bubbles:true}));",
        field.getWrappedElement(), value);
      sleep(150);
      return;
    }
    executeJavaScript("arguments[0].focus();", field.getWrappedElement());
    try {
      field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
      field.sendKeys(value);
      field.sendKeys(Keys.TAB);
    } catch (WebDriverException blocked) {
      executeJavaScript(
        "const el=arguments[0],value=arguments[1];"
          + "const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
          + "setter.call(el,value);el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:value}));"
          + "el.dispatchEvent(new Event('change',{bubbles:true}));el.dispatchEvent(new Event('blur',{bubbles:true}));",
        field.getWrappedElement(), value);
    }
    sleep(150);
  }

  private static void setAndDispatch(SelenideElement field, String value) {
    field.setValue(value);
    dispatch(field);
    sleep(100);
  }

  private static void dispatch(SelenideElement field) {
    executeJavaScript(
      "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
      field.getWrappedElement());
  }

  private static String numericValue(String raw) {
    String value = safe(raw).replace("\u00a0", "").replace(" ", "").replace("'", "");
    if (value.contains(",") && !value.contains(".")) value = value.replace(',', '.');
    else value = value.replace(",", "");
    return value.matches("-?[0-9]+(?:\\.[0-9]+)?") ? value : "";
  }

  private static double numericDouble(String raw) {
    String value = numericValue(raw);
    try { return value.isBlank() ? 0.0 : Double.parseDouble(value); }
    catch (NumberFormatException ignored) { return 0.0; }
  }

  private static String decimal(double value) {
    if (Math.rint(value) == value) return Long.toString((long) value);
    return Double.toString(value);
  }

  private static boolean isRepairableAibFailure(Throwable failure) {
    Throwable root = rootCause(failure);
    String message = safe(root.getMessage()).toLowerCase(Locale.ROOT);
    return root instanceof ElementClickInterceptedException
      || root instanceof StaleElementReferenceException
      || root.getClass().getSimpleName().contains("ElementClickIntercepted")
      || root.getClass().getSimpleName().contains("StaleElement")
      || message.contains("click intercepted")
      || message.contains("save as draft did not produce")
      || message.contains("repaired save as draft");
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable root = failure;
    while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root == null ? failure : root;
  }

  private static void rethrow(Throwable failure) throws Exception {
    if (failure instanceof Exception exception) throw exception;
    if (failure instanceof Error error) throw error;
    throw new AssertionError("Unexpected AIB disposable failure", failure);
  }

  private static String safe(String value) { return value == null ? "" : value; }
}
