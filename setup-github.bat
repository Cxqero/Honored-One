@echo off
rem Double-click to publish (or update) this mod on GitHub: the source, the release jar and the wiki.
rem Everything it does is written out in tools\github-setup.ps1.
title Honored One - GitHub setup
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\github-setup.ps1" %*
echo.
pause
