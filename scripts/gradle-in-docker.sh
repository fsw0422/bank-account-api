#!/usr/bin/env bash
# Runs Gradle inside a JDK container, so this machine needs only Docker, no JDK.
# Usage: scripts/gradle-in-docker.sh <gradle tasks...>, for example: scripts/gradle-in-docker.sh test
#
# The tests start PostgreSQL with Testcontainers. Inside a container, that works through this
# machine's Docker: the Docker socket is shared with the container, and the tests reach the
# containers they start through host.docker.internal. Testcontainers calls this the
# "Docker wormhole" setup.
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="eclipse-temurin:25-jdk"                 # keep in step with the Dockerfile's build stage
GRADLE_CACHE_VOLUME="bank-account-api-gradle"  # downloads survive between runs and `make down`

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 <gradle tasks...>" >&2
  exit 2
fi

# The project is mounted read-only and copied into the container before Gradle runs. That keeps
# this checkout untouched: no build output, and no root-owned files on Linux.
docker run --rm \
  --volume "$PWD:/src:ro" \
  --volume "$GRADLE_CACHE_VOLUME:/root/.gradle" \
  --volume /var/run/docker.sock:/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  "$IMAGE" \
  bash -c '
    set -euo pipefail
    mkdir -p /work
    tar --create --file - --directory /src \
        --exclude ./.git --exclude ./.gradle --exclude ./build --exclude ./.idea . \
      | tar --extract --file - --directory /work
    cd /work
    exec ./gradlew --no-daemon --console=plain "$@"
  ' gradle-in-docker "$@"
