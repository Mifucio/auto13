@req:BP-14
Feature: "Edit internal role (INT)"
  Requirement BP-14 grounded at /admin/authority-rights via the proven admin authentication path and the observed role editor.

  @req:BP-14
  @direct_management
  Scenario: [admin] "Edit internal role"
    Given I am authenticated in the admin application
    And I navigate to the admin "/admin/authority-rights"
    When I open the observed internal role editor without saving
    Then internal_role_edit_surface_visible
