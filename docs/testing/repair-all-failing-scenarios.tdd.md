# Repair all failing scenarios: TDD evidence

## Source and user journey

This task was requested directly on 2026-09-02 after updating `auto13` to `main` revision `2b7f894`.

As the Nasdaq eServices test operator, I want every scenario in the generated suite to execute successfully against the development environment so that failures represent product regressions rather than stale automation, selectors, or harness behavior.

## RED baseline

The baseline was executed before implementation changes:

- Browser bootstrap: `HEADED=false ./gradlew --no-daemon --console=plain browserSmoke`
  - Result: PASS. Chrome 152 and Selenium Manager started a browser successfully.
- Structural suite: `./gradlew --no-daemon --console=plain cleanTest test -Dcucumber.execution.dry-run=true`
  - Result: FAIL. Five signing scenarios reference undefined step `I click Sign Document for the disposable application`.
- Live suite: `HEADED=false ./gradlew --no-daemon --console=plain cleanTest test`
  - Result: FAIL after 1h 15m 45s.
  - JUnit result: 56 scenarios, 27 passed, 29 failed, 0 skipped, 0 errors.
  - HTML report: `build/reports/tests/test/index.html`.

## Failure-to-repair map

| Group | Failing scenarios | RED evidence | Intended guarantee |
|---|---:|---|---|
| Form field mapping | 13 | AIB cannot find `Additional nominal value (added)`; Interest Payment cannot find `Total interest payment amount`; Dividend cannot find `Total issued shares` | Current forms are filled through stable IDs, aliases, or label-normalized lookup |
| Network business wait | 6 | `HANG_DETECTED` on notifications, events, announcements, config, forms, and application-list requests | Background/noise requests do not fail an otherwise completed step; genuinely required business data remains bounded |
| Signing | 1 live plus 5 structural | Undefined Sign Document step; signing workflow did not activate | Every signing phrase is bound and the UI transition is verified with bounded recovery |
| Downloads | 1 | Download click produced no non-empty file | A successful download creates an observed non-empty artifact |
| Role editing | 2 | External role remained on edit route; internal role target was not found | Role selection and save/restore use current UI behavior |
| Authentication | 3 | Missing identifier field, customer login remained on `/login`, admin login was already authenticated | Login helpers accept valid existing sessions and locate current login controls |
| DOM interaction | 3 | Reject click intercepted; external-user and person search selectors stale | Controls are located semantically and clicked only when interactable |

## GREEN evidence

Pending implementation and focused reruns. The task is complete only after focused checks and the complete 56-scenario live suite pass.
