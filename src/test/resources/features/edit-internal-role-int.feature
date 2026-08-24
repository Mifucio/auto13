@req:BP-14
Feature: "Edit internal role (INT)"
  Requirement BP-14 grounded at /admin/authority-rights via goto /login · login(Log In) · submit · goto /admin/authority-rights

  @req:BP-14
  @direct_management
  Scenario: [admin] "Edit internal role"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/admin/authority-rights"
    When I open the observed internal role editor without saving
    Then internal_role_edit_surface_visible
