@req:CA-21
Feature: "Create application → open new form creation page"

  @req:CA-21
  @direct_customer_ca
  Scenario: "Create application → open new form creation page"
    Given I navigate to "/login"
    And I am on the application
    And I submit the observed form
    And I select the represented company card "ICELANDIC COMPANY NES-52 0000000001"
    And I ensure customer application language is English
    When I open Corporate Actions from the customer menu
    And I click Create Application
    And I choose the observed "Bonus Issue" application type
    Then the Application data form must be visible
