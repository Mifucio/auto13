@req:CA-24
@cross_surface_disposable_admin
Feature: "Download attachment from application"
  Requirement CA-24 creates one known disposable attachment through the customer surface and verifies its admin-side download.

  Scenario: [admin] download one known disposable uploaded attachment
    Given a fresh disposable customer Bonus Issue draft with a persisted attachment is opened in the admin application
    When I download the persisted disposable attachment from the admin application
    Then the persisted disposable attachment download exists
