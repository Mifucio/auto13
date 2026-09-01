@echo off
setlocal enabledelayedexpansion

rem ─────────────────────────────────────────────────────────────
rem  run-full-suite.cmd — run ALL features of the Nasdaq
rem  disposable Corporate Actions test suite.
rem
rem  Place this alongside gradlew.bat, or run from any location.
rem ─────────────────────────────────────────────────────────────

set "ROOT=C:\Users\raimo\IdeaProjects\nasdaq-disposable-corporate-actions-tests"
cd /d "%ROOT%"

rem ── Environment ──────────────────────────────────────────────
set "CREDS=%ROOT%\credentials.local.properties"
set "AUTH_CREDENTIALS_FILE=%CREDS%"
set "ADMIN_CREDENTIALS_FILE=%CREDS%"
set "LOCAL_CREDENTIALS_FILE=%CREDS%"

rem ── ChromeDriver ─────────────────────────────────────────────
set "CHROMEDRIVER_PATH=C:\Users\raimo\.cache\selenium\chromedriver\win64\151.0.7922.138\chromedriver.exe"
set "CHROME_NO_SANDBOX=true"
set "HEADED=false"

echo ╔═══════════════════════════════════════════════════════════╗
echo ║  Nasdaq eServices — Full Test Suite                      ║
echo ║  ChromeDriver: %CHROMEDRIVER_PATH%  ║
echo ║  HEADED:       %HEADED%                                   ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

call gradlew.bat --no-daemon --console=plain test
set "RC=%ERRORLEVEL%"

echo.
if %RC% equ 0 (
    echo ✅ SUCCESS — All tests passed!
) else (
    echo ❌ FAILED — %RC% tests/scenarios failed.
    echo    See: %ROOT%\build\reports\tests\test\index.html
)

endlocal & exit /b %RC%