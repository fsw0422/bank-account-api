#!/usr/bin/env bash
# Regenerates the jOOQ classes in jooq-generated/ from the migrations in
# src/main/resources/db/migration. Run it (or `make jooq`) after changing a migration,
# then commit the migration and the generated classes together. Needs Docker.
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE="postgres:18-alpine" # keep in step with docker-compose.yml and TestcontainersConfiguration
CONTAINER="bank-account-api-jooq-$$"
MIGRATIONS="src/main/resources/db/migration"

cleanup() { docker rm --force "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> Starting a throwaway PostgreSQL"
# Trust auth, so no password exists. The port is random and bound to this machine only.
docker run --detach --rm --name "$CONTAINER" \
  --env POSTGRES_DB=bank --env POSTGRES_USER=bank --env POSTGRES_HOST_AUTH_METHOD=trust \
  --publish 127.0.0.1::5432 "$IMAGE" >/dev/null
PORT=$(docker port "$CONTAINER" 5432/tcp | head -1 | sed 's/.*://')

# Check over TCP: during first start-up the image runs a temporary server on a socket only.
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER" pg_isready --quiet --host=127.0.0.1 --username=bank --dbname=bank; then
    break
  fi
  sleep 0.5
done

echo "==> Applying migrations"
# Plain SQL migrations in version order, as Flyway applies them.
for migration in $(ls "$MIGRATIONS"/V*__*.sql | sort -V); do
  echo "    $(basename "$migration")"
  docker exec --interactive "$CONTAINER" \
    psql --quiet --host=127.0.0.1 --username=bank --dbname=bank --set=ON_ERROR_STOP=1 <"$migration"
done

echo "==> Generating jOOQ classes"
./gradlew --quiet jooqCodegen -PjooqJdbcUrl="jdbc:postgresql://127.0.0.1:$PORT/bank"

echo "==> Done. Review and commit the changes in jooq-generated/"
