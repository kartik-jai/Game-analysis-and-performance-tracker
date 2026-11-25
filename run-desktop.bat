@echo off
title Valorant Analytics ^& Performance Tracker - Java Desktop GUI
echo =========================================================================
echo   Compiling ^& Launching Java Desktop GUI (ValorantTrackerApp)...
echo =========================================================================

if not exist out mkdir out

javac -encoding UTF-8 -d out src\model\*.java src\utility\*.java src\service\*.java src\main\*.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed. Make sure Java JDK 11+ is installed.
    pause
    exit /b %errorlevel%
)

if exist lib\sqlite-jdbc.jar (
    java -cp "out;lib\sqlite-jdbc.jar" main.ValorantTrackerApp
) else (
    java -cp "out" main.ValorantTrackerApp
)
