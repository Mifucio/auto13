# Nasdaq eServices — Disposable Corporate Actions application test automation

Automated end-to-end tests for the **Nasdaq eServices** disposable Corporate Actions
application lifecycle using the **Dokobit Mobile ID** test identity.

The suite covers four application types that follow the same
**create → sign → download → History → Attachments** flow:

| Application type | Feature file | Tag |
| --- | --- | --- |
| Dividend Payment | `disposable-dividend` | `@direct_disposable_dividend` |
| Bonus Issue | `disposable-bonus-issue.feature` | `@direct_disposable_bonus` |
| Interest Payment | `disposable-interest-payment.feature` | `@direct_disposable_interest` |
| Additional issuance of Bonds | `disposable-additional-bonds.feature` | `@direct_disposable_aib` |

## What each scenario verifies

For every application type the suite performs the full lifecycle:

1. **Create** — log in via Dokobit Mobile ID, select the test company,
   open Corporate Actions, choose the application type, fill the type-specific form
   (radio groups, selects, checkboxes, dates, the bonds-held table where applicable),
   save as a draft, and read back the application ID.
2. **Sign** — open the saved draft, click **Sign Document**, initiate the Dokobit
   signing process, confirm with the Mobile ID phone, verify the signature is valid,
   and download the signed `.asice` document.
3. **History** — verify the History tab shows the `created application` record and,
   once signed, the `signed application` record.
4. **Attachments** — verify the Attachments tab is active and reachable.

The Dividend Payment flow is the reference (tag `@direct_disposable_dividend`,
features `sign-disposable-dividend.feature` + `view-disposable-history-attachments.feature`).

## Technology stack

- Java + Gradle (Gradle wrapper `gradlew` / `gradlew.bat`)
- Cucumber (BDD `.feature` files in `src/test/resources/features/`)
- Selenium WebDriver via Selenide
- **Chromium** (Google Chrome, default; Microsoft Edge supported) against the
  Nasdaq eServices dev environment. Firefox is a legacy opt-in only.
- Dokobit Mobile ID test identity for electronic signing
- mTLS client certificate (PKCS#12) auto-selected by the browser

## Project layout

```
build.gradle.kts                       Gradle build configuration
run-live.cmd                           Windows launcher (Chromium)
credentials.local.properties.example   Credentials + mTLS template (copy it)
WINDOWS_SETUP.md                       Clean-machine / Windows setup guide
src/test/java/steps/                   Step definitions and support classes
  DisposableDividendSteps.java         Shared disposable-application steps (all 4 types)
  CorporateActionsTabProbe.java        Common tab (History/Attachments) probe
  AuthSteps.java / AuthSupport.java    Login and company-selection support
  Ca22Steps / Ca23Steps / Ca26Steps    Per-flow steps
  regression/                          Evidence capture and reporting helpers
  runner/TestRunner.java               Cucumber runner
src/test/resources/features/           Gherkin feature files
src/test/resources/fixtures/           Test fixtures (e.g. CA-22 attachment)
```

## Running the tests

Configuration is driven by a single file `credentials.local.properties`
(copy `credentials.local.properties`; contains customer/admin logins and
the `mtls.cert`/`mtls.password` for the client certificate). See `WINDOWS_SETUP.md`.

**Windows (Chromium):**
```
run-live.cmd
```
`DIRECT_TAG` selects one of the four application-type tags:
`@direct_disposable_dividend`, `@direct_disposable_bonus`,
`@direct_disposable_interest`, `@direct_disposable_aib`.

**Linux (CI/developer, Chromium or Firefox):**
```
./run-java-live.sh HEADED=true DIRECT_TAG='@direct_disposable_interest'
```


## Current status

See `STATUS.md` for the up-to-date per-flow results on the live dev environment.

## Full test inventory (all feature files in this archive)

The archive contains the complete automated suite. All feature files under
`src/test/resources/features/` are included. Scenario count per feature
(**53 scenarios total**, a superset of the 38 scenarios in the original request):

| # | Feature | Scenarios |
| --- | --- | --- |
| 1 | `add-attachment-to-new-application` | Add one disposable attachment before the first save |
| 2 | `assign-application-to-internal-user` | [admin] Assign Application to internal user |
| 3 | `attach-a-file-in-attachments-tab` | [admin] Attach a file in Attachments tab |
| 4 | `choose-which-company-to-represent` | Choose which company to represent |
| 5 | `create-application-open-new-form-creation-page` | Create application → open new form creation page |
| 6 | `disposable-bonus-issue` | **4** — create → sign+download → History → Attachments |
| 7 | `disposable-interest-payment` | **4** — create → sign+download → History → Attachments |
| 8 | `disposable-additional-bonds` | **4** — create → sign+download → History → Attachments |
| 9 | `download-application` | [admin] Download application |
| 10 | `download-attachment-from-application` | [admin] download one observed uploaded attachment |
| 11 | `download-saved-application-check-if-printout-is-generated` | [admin] Download saved application / printout |
| 12 | `edit-external-role` | [admin] Edit external role |
| 13 | `edit-internal-role` | [admin] Edit internal role |
| 14 | `edit-internal-user` | [admin] Edit internal user |
| 15 | `edit-person` | [admin] Edit Person |
| 16 | `filter-persons` | [admin] Filter Persons |
| 17 | `initiate-signature-process-view-signees` | [admin] Initiate signature process, view signees |
| 18 | `open-home-page` | Open Home page |
| 19 | `open-home-page-2` | [admin] Open Home page |
| 20 | `open-persons-list` | [admin] Open Persons list |
| 21 | `open-roles-external-roles-list` | [admin] Open Roles → External roles list |
| 22 | `open-roles-internal-roles-list` | [admin] Open Roles → Internal roles list |
| 23 | `open-user-settings-make-and-save-changes` | Open User Settings, make and save changes |
| 24 | `open-users-external-users-list` | [admin] Open Users → External users list |
| 25 | `open-users-internal-users-list` | [admin] Open Users → Internal users list |
| 26 | `reject-application-add-comments-check-if-status-changes-to-invalid` | [admin] Reject Application → Invalid |
| 27 | `save-new-application` | Create and preserve one disposable Dividend Payment draft |
| 28 | `search-corporate-actions-list` | [admin] Search Corporate actions list |
| 29 | `search-external-user` | [admin] Search External user |
| 30 | `search-persons` | [admin] Search Persons |
| 31 | `sign-application-via-dokobit` | [admin] Sign one disposable application via Dokobit |
| 32 | `sign-disposable-dividend` | Sign the saved disposable Dividend Payment application |
| 33 | `user-login-via-dokobit-smart-id-or-mobile-id` | User login via Dokobit (Smart-ID or Mobile ID) |
| 34 | `user-manual-login` | [admin] User manual login |
| 35 | `view-attachments-tab` | [admin] View Attachments tab |
| 36 | `view-attachments-tab-2` | [admin] View Attachments tab |
| 37 | `view-corporate-actions-application-list-browse-different-tabs` | [admin] View CA application list / tabs |
| 38 | `view-disposable-history-attachments` | **2** — History + Attachments of the disposable application |
| 39 | `view-history-tab` | [admin] View History tab |
| 40 | `view-history-tab-2` | [admin] View History tab |
| 41 | `view-signatures-tab` | [admin] View Signatures tab |
| 42 | `view-single-application` | [admin] View single application |
| 43 | `view-upcoming-events-in-home-page` | [admin] View upcoming events in home page |

The **disposable Corporate Actions** flows (Bonus Issue, Interest Payment,
Additional issuance of Bonds, and the reference Dividend Payment) are the work
currently being finalised on the live dev environment; the rest of the suite is
included in the archive and covered by the same harness.

