@echo off
REM Lingjiuw CMS launcher: clean cache -> build (admin-ui + backend) -> run.
REM
REM dev.ps1 is an unsigned local script; some machines refuse to run those with the
REM default execution policy ("... is not digitally signed"). -ExecutionPolicy Bypass
REM applies to this one child process only, it does not change any machine setting.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1" %*
