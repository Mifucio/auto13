@req:CA-40
@admin_only_ca_reject
Feature: "Reject Application, add comments, check if status changes to Invalid"
  Requirement CA-40 uses the proven admin authentication path and the observed disposable Submitted Bonus Issue application. It opens the SECOND visible disposable row (ISIN LT0000100527 / company AutotestLtSingleSignee — the disposable test accumulation) so the newest disposable row and the live NES2048 fixtures stay untouched, rejects it with a comment and asserts the status changes to Invalid"."



  @req:CA-40
  Scenario: [admin] "Reject Application, add comments, check if status changes to Invalid"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed disposable Submitted Bonus Issue application not the newest
    When I reject the observed application with comment "Automated test rejection"
    Then the observed application status is Invalid