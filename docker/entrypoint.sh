#!/bin/sh
# Starts the bundled Postgres unless APIPILOT_DB_URL points at another one, then runs the app.
set -e

if [ -z "$APIPILOT_DB_URL" ]; then
  export PGDATA=/data/postgres
  if [ ! -s "$PGDATA/PG_VERSION" ]; then
    # Trust auth is safe here: the server only listens on the container's loopback.
    initdb --username=apipilot --auth=trust --encoding=UTF8 >/dev/null
    first_start=1
  fi
  pg_ctl -w -o "-c listen_addresses=127.0.0.1" -l /data/postgres.log start >/dev/null
  if [ -n "$first_start" ]; then
    createdb --username=apipilot apipilot
  fi
  export APIPILOT_DB_URL=jdbc:postgresql://127.0.0.1:5432/apipilot
  bundled_db=1
fi

java -jar /app/apipilot.jar &
app=$!
trap 'kill -TERM $app' TERM INT
status=0
wait $app || status=$?
# A trapped signal interrupts the first wait; this one waits for the app to finish shutting down.
wait $app 2>/dev/null || true

if [ -n "$bundled_db" ]; then
  pg_ctl -m fast -w stop >/dev/null
fi
exit $status
