@echo off
title Valorant Analytics ^& Performance Tracker - CLI Mode
echo =========================================================================
echo   Compiling Java Packages (model, utility, service, main)...
echo =========================================================================

if not exist out mkdir out

javac -encoding UTF-8 -d out src\model\*.java src\utility\*.java src\service\*.java src\main\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed. Make sure Java JDK 11+ is installed.
    pause
    exit /b %errorlevel%
)

echo.
java -cp out main.ValorantTrackerApp --cli
echo.
pause
