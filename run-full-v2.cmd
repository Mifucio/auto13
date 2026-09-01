@echo off
setlocal enabledelayedexpansion

set "ROOT=C:\Users\raimo\IdeaProjects\nasdaq-disposable-corporate-actions-tests"
cd /d "C:\Users\raimo\IdeaProjects\nasdaq-disposable-corporate-actions-tests"

set "CREDS=%ROOT%\credentials.local.properties"
set "AUTH_CREDENTIALS_FILE=%CREDS%"
set "ADMIN_CREDENTIALS_FILE=%CREDS%"
set "LOCAL_CREDENTIALS_FILE=%CREDS%"

rem Don't set CHROMEDRIVER_PATH — let Selenium Manager auto-download
set "CHROME_NO_SANDBOX=true"
set "HEADED=false"

echo Running full test suite...
call gradlew.bat --no-daemon --console=plain test
set "RC=%ERRORLEVEL%"

echo Exit code: %RC%
endlocal & exit /b %RC%