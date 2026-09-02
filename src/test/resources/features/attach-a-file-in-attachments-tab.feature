@req:CA-39
@admin_only_ca_attach
Feature: "Attach a file in Attachments tab"
  Requirement CA-39 uses the proven admin authentication path: open the newest visible Submitted Corporate Actions application, open its Attachments tab and attach a supported PDF fixture there.



  @req:CA-39
  Scenario: [admin] "Attach a file in Attachments tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the latest visible Submitted Corporate Actions application with form "Bonus Issue"
    And I open the latest observed Corporate Actions "Attachments" tab
    When I attach the supported PDF fixture in the current application Attachments tab
    Then the supported PDF fixture is visible in the current application
