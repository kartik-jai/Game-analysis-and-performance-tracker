@echo off
title Valorant Game Analytics ^& Performance Tracker (Chrome Launcher)
echo =====================================================================
echo   Launching Valorant Analytics ^& Performance Tracker in Chrome...
echo =====================================================================

:: Open the Chrome Web Dashboard immediately so the user sees it right away
start chrome "%~dp0web\index.html" 2>nul || start "" "%~dp0web\index.html"

:: Also compile & start the Java Backend Server if javac is available
where javac >nul 2>nul
if %errorlevel% equ 0 (
    if not exist out mkdir out
    javac -d out src\model\*.java src\utility\*.java src\service\*.java src\main\*.java
    if %errorlevel% equ 0 (
        echo [JAVA BACKEND] Starting Java HTTP Server on http://localhost:8080 ...
        java -cp out main.ValorantWebServer
    )
)
