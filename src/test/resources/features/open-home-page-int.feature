@req:BP-06
Feature: "Open Home page (INT)"
  Requirement BP-06 grounded at /home via goto /login · login(Log In) · submit · goto /home

  @req:BP-06
  @direct_focus
  @direct_admin
  Scenario: [admin] "Open Home page"
    Given I am authenticated in the admin application
    And I navigate to the admin "/home"
    Then the admin home page is displayed

