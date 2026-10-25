@echo off
rem One-click build (Windows). Needs JDK 21 and Gradle 9.2+ installed once.
where gradle >nul 2>nul || (echo Install JDK 21 and Gradle 9.2+ first, then run this again. & pause & exit /b 1)
if not exist gradlew.bat gradle wrapper --gradle-version 9.2.0
call gradlew.bat build
echo.
echo Done. Your mod file: build\libs\patrickszcustoms-1.21.11.jar
pause
