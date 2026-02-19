@echo off
cd /d "%~dp0"

for %%f in (target\*-jar-with-dependencies.jar) do set JAR=%%f

if not defined JAR (
    echo JAR not found. Compiling first...
    call mvn -q -DskipTests compile assembly:single
    for %%f in (target\*-jar-with-dependencies.jar) do set JAR=%%f
)

if not defined JAR (
    echo.
    echo ERROR: Compilation failed. Make sure Java 18+ and Maven are installed.
    pause
    exit /b 1
)

java -jar "%JAR%"
