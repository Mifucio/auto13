@req:CA-30
Feature: "View upcoming events in home page"
  Requirement CA-30 grounded at /home via goto /login · login(Log In) · submit · goto /home

  @req:CA-30
  @direct_focus
  @direct_admin_events
  Scenario: [admin] "View upcoming events in home page"
    Given I am authenticated in the admin application
    And I navigate to the admin "/home"
    Then the admin upcoming events section is displayed

