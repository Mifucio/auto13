@req:CA-23 @req:CA-25 @req:CA-26 @req:CA-29 @direct_disposable_aib
Feature: Create, sign and review the disposable Additional issuance of Bonds application

  Scenario: Create and preserve one disposable Additional issuance of Bonds draft
    Given I log in through Mobile ID for the disposable application
    And I select company "AutotestLtSingleSignee" for the disposable application
    When I open Corporate Actions from the customer menu
    And I click Create Application
    And I choose the last "Additional issuance of Bonds" application type
    Then the Application data form must be visible
    When I fill the disposable "Additional issuance of Bonds" form and save as draft
    Then the Sign Document button must be visible
    And I persist the disposable application ID and remembered source instrument

  Scenario: Sign the saved disposable Additional issuance of Bonds application and download the signed document
    Given the disposable application type is "Additional issuance of Bonds"
    And I open the saved disposable "Additional issuance of Bonds" application
    When I click Sign Document for the disposable application
    Then the Signatures tab and Initiate signing process must be visible
    When I initiate the signing process
    Then the signer full name, signing date, Sign button, and document frame must be visible
    When I click the Sign button for the disposable application
    And I sign the document with Mobile ID phone number "60000666"
    Then Signature is valid must appear within 120 seconds
    When I download the signed disposable document
    Then the signed document must exist in the file system

  Scenario: View the History tab of the saved disposable Additional issuance of Bonds application
    Given the disposable application type is "Additional issuance of Bonds"
    And I open the saved disposable "Additional issuance of Bonds" application
    When I open the "History" tab of the disposable application
    Then the disposable application History must show the created application and, if signed, the signed application

  Scenario: View the Attachments tab of the saved disposable Additional issuance of Bonds application
    Given the disposable application type is "Additional issuance of Bonds"
    And I open the saved disposable "Additional issuance of Bonds" application
    When I open the "Attachments" tab of the disposable application
    Then the disposable application Attachments tab is entered
