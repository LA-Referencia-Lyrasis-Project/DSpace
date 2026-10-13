#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/dspace/src/main/docker-compose/docker-compose.solr.yml"
SOLR_DATA_DIR="${SOLR_DATA_DIR:-/var/solr}"

export SOLR_DATA_DIR

usage() {
    echo "Usage: $0 {start|restart|stop}" >&2
    exit 1
}

[ "$#" -eq 1 ] || usage

case "$1" in
    start)
        exec docker compose -f "$COMPOSE_FILE" up -d --build
        ;;
    restart)
        docker compose -f "$COMPOSE_FILE" stop
        exec docker compose -f "$COMPOSE_FILE" up -d --build --force-recreate
        ;;
    stop)
        exec docker compose -f "$COMPOSE_FILE" stop
        ;;
    *)
        usage
        ;;
esac
