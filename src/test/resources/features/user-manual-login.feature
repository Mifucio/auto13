@req:BP-05
Feature: "User manual login"
  Requirement BP-05 grounded at /login via goto / · goto /login

  @req:BP-05
  @direct_admin_account
  Scenario: [admin] "User manual login"
    Given I navigate to the admin "/"
    And I navigate to the admin "/login"
    When I open the observed manual login form
    And I submit the observed form
    Then home_visible
