@req:CA-25 @req:CA-26 @direct_disposable_history_attachments
Feature: View History and Attachments tabs of a disposable Dividend Payment application

  Scenario: View the History tab of a disposable application
    Given a fresh saved disposable "Dividend Payment" application exists
    When I open the "History" tab of the disposable application
    Then the disposable application History must show the created application and, if signed, the signed application

  Scenario: View the Attachments tab of a disposable application
    Given a fresh saved disposable "Dividend Payment" application exists
    When I open the "Attachments" tab of the disposable application
    Then the disposable application Attachments tab is entered
