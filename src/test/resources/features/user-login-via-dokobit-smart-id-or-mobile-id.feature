@req:BP-01
Feature: User login via Dokobit (Smart-ID or Mobile ID)

  @req:BP-01 @direct_dokobit_mobile_id
  Scenario: User login via Dokobit (Smart-ID or Mobile ID)
    Given I navigate to "/login"
    Then the NASDAQ logo must be populated
    When I pick login language "EN"
    Then "Connect using:" must be populated
    And the following Dokobit login options must be populated in any order:
      | Mobile ID        |
      | Smart-ID         |
      | ID card          |
      | eParaksts Mobile |
      | Audkenni app     |
    And the email input "Enter your email" must be populated
    And the "Okta Sign In" button must be visible but inactive
    And the following login footer values must be populated:
      | Nasdaq CSD          |
      | Tel: +371 6721 2431 |
      | csd@nasdaq.com      |
    When I connect using Dokobit "Mobile ID"
    And I pick Dokobit country "LT"
    And I enter Dokobit phone number "60000666"
    And I enter Dokobit personal code "50001018865"
    And I submit the Dokobit login
    Then I wait until logged in
    And the following represented entities must be populated in any order:
      | MARY ÄNN O’CONNEŽ-ŠUSLIK TESTNUMBER |
      | AutotestLvSingleSignee              |
      | AutotestLtSingleSignee              |
      | AutotestEeSingleSignee              |
      | AutotestIsSingleSignee              |
