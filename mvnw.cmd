@REM Maven Wrapper CMD script
@echo off
setlocal
set "DIR=%~dp0"
if exist "%DIR%mvnw" goto check_mvn
call mvn %*
exit /b %ERRORLEVEL%
:check_mvn
call mvn %*
