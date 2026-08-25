@req:CA-39
@cross_surface_disposable_admin
Feature: "Attach a file in Attachments tab"

  @req:CA-39
  Scenario: [admin] "Attach a file in Attachments tab"
    Given a fresh authenticated disposable admin Corporate Actions draft exists
    When I attach the harmless test fixture in the Attachments tab
    Then the disposable attachment is visible
