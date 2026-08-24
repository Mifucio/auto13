@req:CA-26
@direct_ca_mutating
Feature: "Sign one disposable application via Dokobit"
  Requirement CA-26 is represented by one contract-gated flow. The generated
  company/form matrix and repeated generic actions are intentionally removed.

  @req:CA-26
  Scenario: [admin] "Sign one disposable application via Dokobit through an approved boundary"
    Given the CA-26 disposable identity, application, and reset contracts are approved
    And the CA-26 external signing boundary is approved before confirmation
    When I bind exactly one disposable CA-26 application
    And I prepare the external Dokobit handoff for that disposable CA-26 application
    And I hold the CA-26 signature confirmation at the approved boundary without executing it
    Then the disposable CA-26 application exposes the approved post-sign status contract
