# Current status on the live dev environment

Run of 17 Aug 2026. Each flow runs **4 scenarios** (create → sign+download →
History → Attachments). Counts are `passed / executed`.

| Application type | Result | App ID | History | Attachments | Signed doc |
| --- | --- | --- | --- | --- | --- |
| Dividend Payment | ✅ 4/4 | 123617 | created ✓ signed ✓ | active | yes |
| Bonus Issue | ✅ 4/4 | 123621 | created ✓ signed ✓ | active | yes (`.asice`) |
| Interest Payment | ✅ 4/4 | 123623 | created ✓ signed ✓ | active | yes (`.asice`) |
| Additional issuance of Bonds | ⏳ in progress | — | — | — | — |

Notes:

- Dividend Payment is the reference flow (apps 123614–123617 were exercised while
  hardening the sign/History steps).
- Bonus Issue, Interest Payment and Additional issuance of Bonds were added on top of
  the shared disposable-application machinery (one generic step class + per-type form
  fillers). Bonus and Interest are green.
- **Additional issuance of Bonds** is blocked pending the dev environment recovery
  (backend was returning HTTP 502 during the last runs) and requires finalising the
  type-specific form fillers and the bonds-held table (`aib_bht_*`). The automation
  is in place; the run needs to be completed once the environment is healthy.

Validation diagnostics: when the draft fails to save, the harness now logs the
per-field validation hint text (not just field ids), e.g.
`aib_additional_nominal_value:… -> "…message…"`, to speed up fixing the form fillers.

## Browser / platform direction

- **Default browser is now Chromium** (Google Chrome; Microsoft Edge supported as
  a Chromium engine). Firefox is retained only as an explicit legacy opt-in and is
  no longer a target.
- The suite runs on **Windows** via `run-live.cmd` (see `WINDOWS_SETUP.md`).
  Configuration is a single `credentials.local.properties` (customer/admin logins +
  `mtls.cert`/`mtls.password`). No Firefox, NSS tools, or xvfb are required.
- mTLS on Chromium is handled by `AutoSelectCertificateForUrls` against the OS
  certificate store; the `.p12` also feeds the JVM keyStore for Java-side mTLS.

