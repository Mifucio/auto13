@req:CA-25
@submitted_disposable_admin
Feature: "Initiate signature process, view signees"
  Requirement CA-25 initiates/signs one disposable customer application, then verifies its signees in admin.

  @req:CA-25
  Scenario: [admin] "Initiate signature process, view signees"
    Given a fresh submitted disposable customer Bonus Issue application is opened in the admin application
    When I open the current admin application "Signatures" tab
    Then the admin signees list is visible
