@req:CA-24
@cross_surface_disposable_admin
Feature: "Download attachment from application"
  Requirement CA-24 verifies a known supported attachment on a disposable application and its admin-side download.

  Scenario: [admin] download one known disposable uploaded attachment
    Given a fresh disposable customer Bonus Issue draft is opened in the admin application
    When I attach the supported PDF fixture in the current application Attachments tab
    Then the supported PDF fixture is visible in the current application
    When I download the supported PDF fixture from the current application
    Then the supported PDF fixture download exists
