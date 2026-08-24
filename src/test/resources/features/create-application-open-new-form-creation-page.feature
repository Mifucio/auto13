@req:CA-21
Feature: "Create application → open new form creation page"

  @req:CA-21
  @direct_customer_ca
  Scenario: "Create application → open new form creation page"
    Given I navigate to "/login"
    And I am on the application
    And I submit the observed form
    And I select the observed company "Nasdaq CSD SE LT Branch" to represent
    And I navigate to "/corporate-actions"
    When I open the observed application creation page
    Then the Application data form must be visible
