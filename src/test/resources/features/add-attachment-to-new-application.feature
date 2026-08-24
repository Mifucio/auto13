@req:CA-22
Feature: Add attachment to a new Corporate Actions application
  CA-22 extends the real customer application-creation path from CA-21 and must not bypass it with an admin form-management route.

  @req:CA-22 @direct_customer_ca
  Scenario: Add one disposable attachment before the first save
    Given I navigate to "/login"
    And I am on the application
    And I submit the observed form
    And I select the represented company card "ICELANDIC COMPANY NES-52 0000000001"
    And I ensure customer application language is English
    And I navigate to "/corporate-actions"
    When I open the observed application creation page
    And I choose the observed LV company once for the unsaved CA-22 draft
    And I choose the observed Bonus Issue form once for the unsaved CA-22 draft
    And I stage the harmless CA-22 fixture before the first save
    Then the unsaved CA-22 draft contains exactly one staged disposable attachment
    And I abandon the disposable CA-22 draft without saving
