package steps;

import com.codeborne.selenide.*;
import com.codeborne.selenide.logevents.SelenideLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.*;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.AfterStep;
import io.cucumber.java.en.*;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import io.qameta.allure.selenide.AllureSelenide;
import regression.CheckpointCapture;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LoggingPreferences;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;

import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Condition.*;
import static steps.RuntimeState.*;
import static steps.AuthSupport.*;
import static steps.NetworkMockSupport.*;

public class AuthSteps {

  // ── Locator Constants ─────────────────────────────────────────
  // No locators generated — tests use generic actions

  // ── Step Definitions — domain: auth ───────────────────────

  @Given("I navigate to {string}")
  public void i_navigate_to_string(String param0) {
    open(param0);

  }

  @And("I am on the application")
  public void i_am_on_the_application() {
    assertCurrentOrigin(BASE_URL);

  }

  @When("I open the observed manual login form")
  public void i_open_the_observed_manual_login_form() {
    openObservedManualLoginForm();
  }

  @Then("company_context_applied")
  public void company_context_applied() {
    assertCompanyContextApplied();
    CheckpointCapture.capture("choose-which-company-to-represent.choose-which-company-to-represent.company-context-applied");
  }

  @When("I select the observed company {string} to represent")
  public void i_select_the_observed_company_to_represent(String companyName) {
    selectObservedCompanyToRepresent(companyName);
  }

  @When("I open the observed user settings editor without saving")
  public void i_open_the_observed_user_settings_editor_without_saving() {
    openObservedUserSettingsEditorWithoutSaving();
  }

  @Then("settings_change_round_trip_saved_without_net_change")
  public void settings_change_round_trip_saved_without_net_change() {
    assertSettingsChangeRoundTripSavedWithoutNetChange();
    screenshot("direct-customer-settings-round-trip-saved");
  }

  @When("I authenticate through Dokobit Mobile-ID with phone {string} and personal code {string}")
  public void authenticateThroughDokobitMobileId(String phone, String personalCode) {
    dokobitLogin("Mobile ID", phone, personalCode);
  }

  @When("I connect using Dokobit {string}")
  public void iConnectUsingDokobit(String provider) {
    openDokobitProvider(provider);
  }

  @Then("the NASDAQ logo must be populated")
  public void theNasdaqLogoMustBePopulated() {
    assertNasdaqLogoPopulated();
  }

  @When("I pick login language {string}")
  public void iPickLoginLanguage(String language) {
    pickLoginLanguage(language);
  }

  @Then("{string} must be populated")
  public void textMustBePopulated(String text) {
    assertVisibleTextPopulated(text);
  }

  @Then("the following Dokobit login options must be populated in any order:")
  public void dokobitLoginOptionsMustBePopulated(DataTable table) {
    assertLoginOptionsPopulated(table.asList());
  }

  @Then("the email input {string} must be populated")
  public void emailInputMustBePopulated(String placeholder) {
    assertEmailInputPopulated(placeholder);
  }

  @Then("the {string} button must be visible but inactive")
  public void buttonMustBeVisibleButInactive(String label) {
    assertControlVisibleButInactive(label);
  }

  @Then("the following login footer values must be populated:")
  public void loginFooterValuesMustBePopulated(DataTable table) {
    assertLoginFooterPopulated(table.asList());
  }

  @When("I pick Dokobit country {string}")
  public void iPickDokobitCountry(String country) {
    pickDokobitCountry(country);
  }

  @When("I enter Dokobit phone number {string}")
  public void iEnterDokobitPhoneNumber(String phone) {
    enterDokobitPhone(phone);
  }

  @When("I enter Dokobit personal code {string}")
  public void iEnterDokobitPersonalCode(String personalCode) {
    enterDokobitPersonalCode(personalCode);
  }

  @When("I submit the Dokobit login")
  public void iSubmitTheDokobitLogin() {
    submitDokobitLogin();
  }

  @Then("I wait until logged in")
  public void iWaitUntilLoggedIn() {
    awaitAuthenticatedCustomer();
  }

  @Then("the following represented entities must be populated in any order:")
  public void representedEntitiesMustBePopulatedInAnyOrder(DataTable table) {
    assertRepresentedEntitiesPopulated(table.asList());
  }

  @Then("the Dokobit authentication result should be {string}")
  public void assertDokobitAuthenticationResult(String expectedResult) {
    String normalized = expectedResult.trim().toLowerCase(java.util.Locale.ROOT);
    if ("success".equals(normalized)) {
      awaitAuthenticatedCustomer();
      String body = $("body").shouldBe(visible).getText();
      if (!(body.contains("Choose who you represent") || body.contains("Nasdaq eServices")
          || body.contains("Corporate Actions") || body.contains("Security Holders"))) {
        throw new AssertionError("Successful Dokobit authentication did not expose an authenticated customer surface");
      }
      return;
    }
    Map<String, List<String>> expectedSignals = Map.of(
      "no_active_certificates", List.of(
        "certificate", "not active", "no active",
        "not a mobile id user", "mobile id activated"),
      "request_failed", List.of("request", "failed", "error"),
      "user_refused", List.of("refused", "cancelled", "canceled"),
      "invalid_signature", List.of("signature", "invalid"),
      "sim_error", List.of("sim", "error"),
      "no_coverage", List.of("coverage", "unreachable", "not available"),
      "timeout", List.of("timeout", "timed out", "expired")
    );
    List<String> signals = expectedSignals.get(normalized);
    if (signals == null) throw new AssertionError("Unsupported Dokobit expected result: " + expectedResult);
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String body = $("body").shouldBe(visible).getText().toLowerCase(java.util.Locale.ROOT);
      if (signals.stream().anyMatch(body::contains)) return;
      sleep(200);
    }
    throw new AssertionError("Dokobit result did not expose the expected " + normalized
      + " failure surface after waiting for the asynchronous provider result");
  }

  @Then("dashboard_visible")
  public void dashboard_visible() {
    assertCompanyContextApplied();
    screenshot("direct-customer-dokobit-dashboard-visible");
  }
}
