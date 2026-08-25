@req:CA-39
@cross_surface_disposable_admin
Feature: "Attach a file in Attachments tab"

  @req:CA-39
  Scenario: [admin] "Attach a file in Attachments tab"
    Given a fresh disposable customer Bonus Issue draft is opened in the admin application
    When I attach the harmless test fixture in the Attachments tab
    Then the disposable attachment is visible
