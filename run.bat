@echo off
cd /d "%~dp0"

set JAR=target\itunes-backup-explorer-1.7-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR%" (
    echo JAR not found. Compiling first...
    call mvn -q -DskipTests compile assembly:single
)

java -jar "%JAR%"
