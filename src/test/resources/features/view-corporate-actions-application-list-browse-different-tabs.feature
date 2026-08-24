@req:CA-31
@direct_ca_readonly
Feature: "View Corporate actions Application list, browse different tabs"
  Requirement CA-31 uses the proven admin authentication path and the observed Corporate Actions list.

  @req:CA-31
  Scenario: [admin] "View Corporate actions Application list, browse different tabs"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    Then application_list_visible
