@req:CA-31
@direct_ca_readonly
Feature:"View Corporate actions Application list, browse different tabs"
  Requirement CA-31 uses the proven admin authentication path and clicks through
  each tab of the Corporate Actions application list, asserting each one opens
  (the clicked tab becomes the active `li.tab-item.active`).

  @req:CA-31
  Scenario: [admin] "View Corporate actions Application list, browse different tabs"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    Then application_list_visible
    When I browse all the Corporate Actions application list tabs
    Then each opened Corporate Actions list tab stays open