#!/usr/bin/env bash
# Build all Spring Boot service jars and the load test client distribution,
# then build all Docker images. Run from the project root or infra/ directory.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo ">>> Building Java artifacts (gradle assemble + installDist)"
./gradlew --no-daemon \
    :common-api:assemble :common-core:assemble \
    :service-iam:bootJar :service-metadata:bootJar :service-placement:bootJar \
    :service-data-node:bootJar :service-data-routing:bootJar :service-api:bootJar \
    :service-gc:bootJar \
    :client-loadtest:installDist \
    -x test

echo ">>> Building Docker images"
cd "$ROOT/infra"
docker compose --profile test build "$@"

echo ">>> Done."
