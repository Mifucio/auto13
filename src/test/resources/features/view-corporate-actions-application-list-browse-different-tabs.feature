@req:CA-31
@direct_ca_readonly
Feature: "View Corporate actions Application list, browse different tabs"
  Requirement CA-31 grounded at /corporate-actions via direct admin login · goto /corporate-actions

  @req:CA-31
  Scenario: [admin] "View Corporate actions Application list, browse different tabs"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    Then application_list_visible
