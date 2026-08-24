@req:BP-10
Feature: "Edit internal user (INT)"
  Requirement BP-10 grounded at /admin/user-management via goto /login · login(Log In) · submit · goto /admin/user-management

  @req:BP-10
  @direct_management
  @edit_internal_user
  Scenario: [admin] "Edit internal user"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/admin/user-management"
    When I open the observed internal user editor without saving
    Then internal_user_edit_surface_visible
