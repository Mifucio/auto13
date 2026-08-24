@req:BP-04
Feature: "Open User Settings, make and save changes"
  Requirement BP-04 grounded at /login via goto /login · login(Okta Sign In) · submit · goto /login

  @req:BP-04
  @direct_customer_account
  Scenario: "Open User Settings, make and save changes"
    Given I navigate to "/login"
    And I am on the application
    And I submit the observed form
    And I select the represented company card "ICELANDIC COMPANY NES-52 0000000001"
    And I ensure customer application language is English
    When I open the observed user settings editor without saving
    Then settings_change_round_trip_saved_without_net_change
