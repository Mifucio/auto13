@req:CA-25 @req:CA-26 @req:CA-29 @direct_disposable_signing
Feature: Sign and download the disposable Dividend Payment application

  Scenario: Sign the saved disposable Dividend Payment application and download the signed document
    Given I open the saved disposable Dividend Payment application
    When I click Sign Document for the disposable application
    Then the Signatures tab and Initiate signing process must be visible
    When I initiate the signing process
    Then the signer full name, signing date, Sign button, and document frame must be visible
    When I click the Sign button for the disposable application
    And I sign the document with Mobile ID phone number "60000666"
    Then Signature is valid must appear within 120 seconds
    When I download the signed disposable document
    Then the signed document must exist in the file system
