@req:CA-23
Feature: Create disposable Dividend Payment application

  @req:CA-23 @direct_disposable_dividend
  Scenario: Create and preserve one disposable Dividend Payment draft
    Given I log in through Mobile ID for the disposable application
    And I select and verify company "AutotestLtSingleSignee" for the disposable application
    When I open Corporate Actions from the customer menu
    And I click Create Application
    And I choose the last "Dividend Payment" application type
    Then the Application data form must be visible
    When I select and remember a source instrument
    And I set Payment for one security to "1"
    Then Total payment amount must equal Total issued shares multiplied by Payment for one security
    When I set Date of general meeting within the past 30 days
    And I set Net dividend amount transferred to paying agent to the calculated total payment amount
    And I add two random Excluded accounts rows
    And I set Ex-date within the next 7 days, retrying another date on validation error
    Then Record date and Payment date must be populated
    When I save the disposable application as draft, filling mandatory fields and attaching a PDF if required
    Then the Sign Document button must be visible
    And I persist the disposable application ID and remembered source instrument
