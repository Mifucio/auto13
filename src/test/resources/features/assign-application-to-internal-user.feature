@req:CA-33
@ca33_assignment_repair
Feature: "Assign Application to internal user"

  @req:CA-33
  Scenario: [admin] "Assign Application to internal user"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I assign the latest observed Submitted Bonus Issue to an available CSD user
    Then the latest observed Submitted Bonus Issue retains the assigned CSD user
