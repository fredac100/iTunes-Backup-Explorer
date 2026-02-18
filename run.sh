#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

JAR="target/itunes-backup-explorer-1.7-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JAR" ]; then
    echo "JAR not found. Compiling first..."
    mvn -q -DskipTests compile assembly:single
fi

java -jar "$JAR"
