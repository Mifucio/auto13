@req:CA-25
@direct_ca_disposable_draft
Feature: "Initiate signature process, view signees"
  Requirement CA-25 is exercised on one disposable application. The previous
  16-row country/form matrix came from Form management "Countries enabled"
  metadata and did not represent 16 actual application instances.

  @req:CA-25
  Scenario: [admin] "Initiate signature process, view signees"
    Given a fresh authenticated disposable admin Corporate Actions draft exists
    When I initiate signing and open the disposable signees surface
    Then the disposable signees surface is visible
