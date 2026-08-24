@req:CA-28
@direct_ca_readonly
Feature: "View Attachments tab"
  Requirement CA-28 grounded at /corporate-actions/form via direct admin login · observed existing application from /corporate-actions

  @req:CA-28
  Scenario Outline: [admin] "View Attachments tab" for <company>, <caForm>
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "<company>", "<caForm>" without saving
    And I open the observed "Attachments" Corporate Actions tab without saving
    Then attachments_list_visible

    # The live read-only boundary uses one observed existing application. The
    # original generated 16-row matrix fabricated company/form combinations.
    Examples:
      | company | caForm |
      | LV | bonus |
