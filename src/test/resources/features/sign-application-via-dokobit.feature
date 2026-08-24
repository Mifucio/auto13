@req:CA-26
@direct_ca_mutating
Feature: "Sign one disposable application via Dokobit"
  Requirement CA-26 must execute the real browser signing flow and observe the provider/application result.

  @req:CA-26
  Scenario: Sign one disposable Dividend Payment application via Dokobit
    Given a fresh saved disposable "Dividend Payment" application exists
    When I click Sign Document for the disposable application
    Then the Signatures tab and Initiate signing process must be visible
    When I initiate the signing process
    Then the signer full name, signing date, Sign button, and document frame must be visible
    When I click the Sign button for the disposable application
    And I sign the document with Mobile ID phone number "60000666"
    Then Signature is valid must appear within 120 seconds
    When I download the signed disposable document
    Then the signed document must exist in the file system
