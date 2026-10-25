#!/bin/sh
# One-click build (macOS / Linux). Needs JDK 21 and Gradle 9.2+ installed once.
command -v gradle >/dev/null 2>&1 || { echo "Install JDK 21 and Gradle 9.2+ first."; exit 1; }
[ -f gradlew ] || gradle wrapper --gradle-version 9.2.0
./gradlew build
echo "Done. Your mod file: build/libs/patrickszcustoms-1.21.11.jar"
