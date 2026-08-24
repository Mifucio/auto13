@req:BP-12
Feature: "Edit external role (INT)"
  Requirement BP-12 grounded at /external/admin/authority-rights via goto /login · login(Log In) · submit · goto /external/admin/authority-rights

  @req:BP-12
  @direct_management
  @edit_external_role
  Scenario: [admin] "Edit external role"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/external/admin/authority-rights"
    When I fill "Search query" with "AutotestRole"
    And I click "Search"
    And I open the observed external role "AutotestRole" editor
    Then the observed external role editor is displayed
    When I remember the current role description
    And I append "1" to the role description
    And I select "company" as the user representing
    And I select "EE" as the represented entity country
    And I add the role right "EHI_IS_DI_RE"
    And I save the external role
    Then role_saved_confirmation