@new_scenario
Feature: Mock expired signature for a disposable Dividend Payment application
  Creates a Dividend Payment draft, initiates the signing process, mocks an
  expired Dokobit session, and captures the resulting error message.

  @new_scenario
  Scenario: Mock expired signature on a Dividend Payment draft
    Given I log in through Mobile ID for the disposable application
    And I select and verify company "AutotestLtSingleSignee" for the disposable application
    And I ensure customer application language is English
    When I open Corporate Actions from the customer menu
    And I click Create Application
    And I choose the observed "Dividend Payment" application type
    Then the Application data form must be visible
    When I select and remember a source instrument
    And I set Payment for one security to "1"
    Then Total payment amount must equal Total issued shares multiplied by Payment for one security
    When I set Date of general meeting within the past 30 days
    And I set Net dividend amount transferred to paying agent to the calculated total payment amount
    And I set Ex-date within the next 7 days, retrying another date on validation error
    Then Record date and Payment date must be populated
    When I reliably save the prepared disposable application as draft
    Then the Sign Document button must be visible
    And I persist the disposable application ID and remembered source instrument
    When I click Sign Document for the disposable application
    Then the Signatures tab and Initiate signing process must be visible
    When I initiate the signing process
    And I mock the Dokobit signing API to return expired session
    When I click the Sign button for the disposable application
    Then an expired signing error message is displayed
    And I capture the expiration error message text