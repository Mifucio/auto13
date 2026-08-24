@req:CA-25
@direct_ca_disposable_draft
Feature: "Initiate signature process, view signees"

  @req:CA-25
  Scenario: [admin] "Initiate signature process, view signees"
    Given a fresh disposable admin Corporate Actions draft exists
    When I initiate signing and open the disposable signees surface
    Then the disposable signees surface is visible
