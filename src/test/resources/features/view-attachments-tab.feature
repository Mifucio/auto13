@req:CA-28
@direct_ca_readonly
Feature: "View Attachments tab"
  Requirement CA-28 uses the proven admin authentication path and one observed existing application.

  @req:CA-28
  Scenario Outline: [admin] "View Attachments tab" for <company>, <caForm>
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "<company>", "<caForm>" without saving
    And I open the observed "Attachments" Corporate Actions tab without saving
    Then attachments_list_visible

    Examples:
      | company | caForm |
      | LV | bonus |
