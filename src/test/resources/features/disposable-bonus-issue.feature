@req:CA-23 @req:CA-25 @req:CA-26 @req:CA-29 @direct_disposable_bonus
Feature: Create, sign and review the disposable Bonus Issue application

  Scenario: Create and preserve one disposable Bonus Issue draft
    Given I log in through Mobile ID for the disposable application
    And I select and verify company "AutotestLtSingleSignee" for the disposable application
    And I ensure customer application language is English
    When I open Corporate Actions from the customer menu
    And I click Create Application
    And I choose the last "Bonus Issue" application type
    Then the Application data form must be visible
    When I fill and safely save the disposable "Bonus Issue" form as draft
    Then the Sign Document button must be visible
    And I persist the disposable application ID and remembered source instrument

  Scenario: Sign a disposable Bonus Issue application and download the signed document
    Given a fresh saved disposable "Bonus Issue" application exists
    When I click Sign Document for the disposable application
    Then the Signatures tab and Initiate signing process must be visible
    When I initiate the signing process
    Then the signer full name, signing date, Sign button, and document frame must be visible
    When I click the Sign button for the disposable application
    And I sign the document with Mobile ID phone number "60000666"
    Then Signature is valid must appear within 120 seconds
    When I download the signed disposable document through the observed download control
    Then the repaired signed disposable document exists in the file system

  Scenario: View the History tab of a disposable Bonus Issue application
    Given a fresh saved disposable "Bonus Issue" application exists
    When I open the "History" tab of the disposable application
    Then the disposable draft History contains the current application creation event

  Scenario: View the Attachments tab of a disposable Bonus Issue application
    Given a fresh saved disposable "Bonus Issue" application exists
    When I open the "Attachments" tab of the disposable application
    Then the disposable application Attachments tab is entered
