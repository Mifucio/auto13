@echo off
cd /d "C:\Users\raimo\IdeaProjects\nasdaq-disposable-corporate-actions-tests"

rem Don't set CHROMEDRIVER_PATH — let Selenium Manager auto-download
set "CHROME_NO_SANDBOX=true"
set "HEADED=false"

call gradlew.bat --no-daemon --console=plain test
set "RC=%ERRORLEVEL%"
echo Exit code: %RC%
exit /b %RC%