#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/backend" || exit 1
exec ./mvnw "$@"
