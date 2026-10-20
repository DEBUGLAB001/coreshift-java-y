#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

mvn -q -DskipTests package 1>&2

java -jar target/*.jar &
PID=$!

trap 'kill -TERM "$PID" 2>/dev/null; exit 0' SIGTERM SIGINT EXIT

get_snap() {
    find src/main/java -name "*.java" -exec stat -c "%Y %n" {} + 2>/dev/null | sort
}
LAST_SNAP=$(get_snap)

while true; do
    sleep 2
    CUR_SNAP=$(get_snap)
    if [ "$CUR_SNAP" != "$LAST_SNAP" ]; then
        echo "[Engine] Recompiling updated Java files..."
        if mvn -q package -DskipTests; then
            kill -TERM "$PID" 2>/dev/null || true
            wait "$PID" 2>/dev/null || true
            java -jar target/*.jar &
            PID=$!
            echo "[Engine] App reloaded successfully."
        fi
        LAST_SNAP=$(get_snap)
    fi
done
