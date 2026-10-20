@REM ----------------------------------------------------------------------------
@REM NEXUSFLOW Maven Wrapper for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set "MVN_CMD=mvn"
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set "MVN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
    ) else if exist "%USERPROFILE%\Downloads\maven-mvnd-1.0.2-windows-amd64\maven-mvnd-1.0.2-windows-amd64\bin\mvnd.cmd" (
        set "MVN_CMD=%USERPROFILE%\Downloads\maven-mvnd-1.0.2-windows-amd64\maven-mvnd-1.0.2-windows-amd64\bin\mvnd.cmd"
    )
)

if "%1"=="test" goto run_tests
if "%2"=="test" goto run_tests
if "%1"=="spring-boot:run" goto run_app
if "%2"=="spring-boot:run" goto run_app

"%MVN_CMD%" %*
goto end

:run_app
if exist "%DIRNAME%target\app.jar" (
    java -jar "%DIRNAME%target\app.jar"
    goto end
)
"%MVN_CMD%" spring-boot:run
goto end

:run_tests
"%MVN_CMD%" test
goto end

:end
endlocal
