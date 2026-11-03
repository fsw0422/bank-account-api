.DEFAULT_GOAL := help
.PHONY: help build test run up down logs smoke jooq

help: ## Show this list
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "} {printf "  make %-6s %s\n", $$1, $$2}'

build: ## Compile, run all tests, and package the jar, inside Docker (no JDK needed)
	./scripts/gradle-in-docker.sh build

test: ## Run all tests inside Docker (no JDK needed)
	./scripts/gradle-in-docker.sh test

run: ## Start the API and PostgreSQL in the foreground
	docker compose up --build

up: ## Start the API and PostgreSQL in the background, wait until healthy
	docker compose up --build --detach --wait

down: ## Stop everything and delete the local database
	docker compose down --volumes

logs: ## Follow the API logs
	docker compose logs --follow app

smoke: ## Call the running API with curl (start it first with: make up)
	./scripts/smoke.sh

jooq: ## Regenerate jOOQ classes in jooq-generated/ after a schema change (needs Docker and JDK 17+)
	./scripts/generate-jooq.sh
