@echo off
rem ─────────────────────────────────────────────────────────────────────
rem  run-live.cmd — run the Nasdaq eServices disposable Corporate Actions
rem  test suite on Windows with Chromium (Chrome/Edge).
rem
rem  Config lives in a single file: credentials.local.properties
rem  (copy credentials.local.properties.example and fill it in).
rem
rem  Optional env overrides:
rem    DIRECT_TAG   Cucumber tag to run (default @direct_disposable_interest)
rem    HEADED       true to show the browser (default true here)
rem    OHTEST_BROWSER  chrome (default) | edge | firefox
rem    CHROMEDRIVER_PATH  path to chromedriver.exe (optional; Selenium
rem                       Manager auto-downloads a matching driver otherwise)
rem ─────────────────────────────────────────────────────────────────────
setlocal
set "ROOT=%~dp0"

set "CREDS=%ROOT%credentials.local.properties"
if not exist "%CREDS%" (
  echo [ERROR] %CREDS% not found.
  echo Copy credentials.local.properties.example to credentials.local.properties
  echo and fill in the customer/admin logins and the mTLS client certificate.
  exit /b 1
)

if not exist "%ROOT%gradlew.bat" (
  echo [ERROR] gradlew.bat not found beside this script.
  exit /b 1
)

rem ── Credentials + mTLS from the single config file ─────────────────
set "AUTH_CREDENTIALS_FILE=%CREDS%"
set "ADMIN_CREDENTIALS_FILE=%CREDS%"
set "LOCAL_CREDENTIALS_FILE=%CREDS%"

rem ── Browser: Chromium by default ────────────────────────────────────
if "%OHTEST_BROWSER%"=="" set "OHTEST_BROWSER=chrome"
if "%HEADED%"=="" set "HEADED=true"
if "%DIRECT_TAG%"=="" set "DIRECT_TAG=@direct_disposable_interest"

cd /d "%ROOT%"
echo Running browser=%OHTEST_BROWSER% HEADED=%HEADED% DIRECT_TAG=%DIRECT_TAG%
call gradlew.bat --no-daemon --rerun-tasks test -Dcucumber.filter.tags="%DIRECT_TAG%"
set "RC=%ERRORLEVEL%"
endlocal & exit /b %RC%
