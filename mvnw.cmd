@echo off
setlocal
cd /d "%~dp0backend"
call mvnw.cmd %*
cd /d "%~dp0"
