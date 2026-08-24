# Running on Windows with Chromium (Chrome/Edge)

This suite no longer depends on Firefox. The default target is **Chromium**
(Google Chrome; Microsoft Edge is also supported as a Chromium engine). This
doc is for a clean Windows machine that only has Java + Gradle.

## 1. Prerequisites

- **JDK 17+** on `PATH` (`java -version`).
- **Google Chrome** installed (or Microsoft Edge).
- Gradle is **not** required separately — the suite ships the Gradle wrapper
  (`gradlew.bat`), which downloads the correct Gradle on first run.
- No Firefox, no NSS `certutil`/`pk12util`, no `xvfb` are needed.

The ChromeDriver is normally downloaded automatically by Selenium Manager to
match your installed Chrome. If you want to pin it, set `CHROMEDRIVER_PATH`
to a `chromedriver.exe` matching your Chrome version.

## 2. mTLS client certificate

The Nasdaq eServices customer origin is behind mutual TLS. The certificate must
be reachable **both** by the browser and by Java:

1. **Browser (Chrome on Windows):** import the client certificate (`.p12`) into
   the **CurrentUser\My** certificate store. Chrome's `AutoSelectCertificateForUrls`
   (already set by the harness) then selects it silently without a prompt:
   ```
   certutil -user -importpfx C:\path\client.p12
   ```
   (or double-click the `.p12` and choose *Current User → Personal*).

2. **Java (JVM mTLS calls):** point the harness at the same `.p12` via the
   `mtls.cert` / `mtls.password` keys in `credentials.local.properties`:
   ```
   mtls.cert=C:\path\client.p12
   mtls.password=...
   ```

## 3. Credentials

Copy `credentials.local.properties` → `credentials.local.properties`
beside the suite and fill in the customer (Dokobit Mobile ID) and admin logins:

```properties
customer.identifier=
customer.password=
customer.dokobit.provider=
customer.dokobit.phone=
customer.dokobit.personalCode=
admin.identifier=
admin.password=
mtls.cert=C:\path\client.p12
mtls.password=
```

Keep this file out of the archive and out of Git.

## 4. Run

```
run-live.cmd
```

Optional overrides (set as env vars or edit the script defaults):

| Variable | Default | Meaning |
| --- | --- | --- |
| `DIRECT_TAG` | `@direct_disposable_interest` | Cucumber tag to run |
| `OHTEST_BROWSER` | `chrome` | `chrome`, `edge`, or `firefox` |
| `HEADED` | `true` | show the browser window |
| `CHROMEDRIVER_PATH` | auto | pinned `chromedriver.exe` |

To run a different flow, e.g. Bonus Issue:

```
set DIRECT_TAG=@direct_disposable_bonus
run-live.cmd
```

Available disposable tags: `@direct_disposable_dividend`, `@direct_disposable_bonus`,
`@direct_disposable_interest`, `@direct_disposable_aib`.

## 5. Result

The runner prints the JUnit-derived counts:
`DIRECT_COUNTS selected=… executed=… passed=… failed=… skipped=… total=…`
plus per-flow evidence (application ID, History/Attachments state, downloaded
`.asice`) in the test logs and reports under `build/reports`.
