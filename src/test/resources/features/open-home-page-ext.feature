@req:BP-03
Feature: "Open Home page"
  Requirement BP-03 grounded at /home via goto /login · login(Okta Sign In) · submit · goto /home

  @req:BP-03
  @direct_focus
  @direct_customer
  Scenario: "Open Home page"
    Given I navigate to "/login"
    And I am on the application
    And I submit the observed form
    And I navigate to "/home"
    Then the customer home page is displayed

