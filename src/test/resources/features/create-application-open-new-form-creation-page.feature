@req:CA-21
Feature: "Create application → open new form creation page"
  Covers the 4 represented companies and the 4 Corporate Actions form types
  with pairwise (company × form) coverage. Customer authentication is
  Mobile ID only; the signing-capable company is AutotestLtSingleSignee.

  @req:CA-21
  Scenario Outline: "Create application → open new form creation page" as <company>
    Given I log in through Mobile ID for the disposable application
    And I select and verify company "<company>" for the disposable application
    And I ensure customer application language is English
    When I open Corporate Actions from the customer menu
    And I click Create Application
    And I choose the observed "<formType>" application type
    Then the Application data form must be visible

    Examples:
      | company                  | formType                     |
      | AutotestLtSingleSignee   | Bonus Issue                  |
      | AutotestLvSingleSignee   | Dividend Payment             |
      | AutotestEeSingleSignee   | Interest Payment             |
      | AutotestIsSingleSignee   | Additional issuance of Bonds |
