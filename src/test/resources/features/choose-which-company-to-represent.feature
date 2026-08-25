@req:BP-02
Feature: "Choose which company to represent"
  Requirement BP-02 grounded at /company-selection via goto /login · login(Okta Sign In) · submit

  @req:BP-02
  @direct_customer_account
  Scenario: "Choose which company to represent"
    Given I navigate to "/login"
    And I am on the application
    And I submit the observed form
    When I select the represented company card "AutotestLtSingleSignee"
    Then company_context_applied
