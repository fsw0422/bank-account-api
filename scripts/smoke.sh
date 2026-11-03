#!/usr/bin/env bash
# Walks through every endpoint with curl and checks each status code and balance.
# Start the API first with `make up`. Needs only bash and curl.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MANAGEMENT_URL="${MANAGEMENT_URL:-http://localhost:9090}"
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

fail() {
  printf '\nSMOKE TEST FAILED: %s\n' "$1" >&2
  exit 1
}

# call <expected status> <method> <url> [json body] [extra header]
call() {
  local expected=$1 method=$2 url=$3 body=${4:-} header=${5:-}
  local args=(--silent --show-error --output "$BODY_FILE" --write-out '%{http_code}' --request "$method" "$url")
  if [[ -n $body ]]; then
    args+=(--header 'Content-Type: application/json' --data "$body")
  fi
  if [[ -n $header ]]; then
    args+=(--header "$header")
  fi
  local status
  status=$(curl "${args[@]}")
  printf '%s %s -> %s\n  %s\n' "$method" "$url" "$status" "$(cat "$BODY_FILE")"
  [[ $status == "$expected" ]] || fail "expected HTTP $expected, got $status"
}

# Reads one top-level field from the last response body (no jq needed).
field() {
  sed -E 's/.*"'"$1"'":"?([^",}]*)"?.*/\1/' "$BODY_FILE"
}

expect_field() {
  local actual
  actual=$(field "$1")
  [[ $actual == "$2" ]] || fail "expected $1=$2, got $actual"
}

step() { printf '\n==> %s\n' "$1"; }

step "Service is ready (includes the database check)"
call 200 GET "$MANAGEMENT_URL/actuator/health/readiness"

step "Create Alice's account with EUR 100.00 (10000 cents)"
call 201 POST "$BASE_URL/accounts" '{"ownerName": "Alice", "currency": "EUR", "initialBalance": 10000}'
ALICE=$(field id)

step "Create Bob's account with a zero balance"
call 201 POST "$BASE_URL/accounts" '{"ownerName": "Bob", "currency": "EUR"}'
BOB=$(field id)

step "Deposit EUR 5.00 into Bob's account"
call 201 POST "$BASE_URL/accounts/$BOB/deposits" '{"amount": 500}'

step "Transfer EUR 25.00 from Alice to Bob, with an idempotency key"
KEY="smoke-$(date +%s)-$$"
TRANSFER_BODY="{\"sourceAccountId\": \"$ALICE\", \"targetAccountId\": \"$BOB\", \"amount\": 2500}"
call 201 POST "$BASE_URL/transfers" "$TRANSFER_BODY" "Idempotency-Key: $KEY"
TRANSFER_ID=$(field id)

step "Retry the same transfer with the same key: same result, no second transfer"
call 201 POST "$BASE_URL/transfers" "$TRANSFER_BODY" "Idempotency-Key: $KEY"
expect_field id "$TRANSFER_ID"

step "Reuse the key for a different amount: rejected"
call 422 POST "$BASE_URL/transfers" \
  "{\"sourceAccountId\": \"$ALICE\", \"targetAccountId\": \"$BOB\", \"amount\": 1}" "Idempotency-Key: $KEY"
expect_field code IDEMPOTENCY_KEY_REUSED

step "Transfer more than Alice has: rejected, nothing changes"
call 422 POST "$BASE_URL/transfers" \
  "{\"sourceAccountId\": \"$ALICE\", \"targetAccountId\": \"$BOB\", \"amount\": 1000000}"
expect_field code INSUFFICIENT_FUNDS

step "Balances: Alice 10000 - 2500 = 7500, Bob 500 + 2500 = 3000"
call 200 GET "$BASE_URL/accounts/$ALICE"
expect_field balance 7500
call 200 GET "$BASE_URL/accounts/$BOB"
expect_field balance 3000

step "Unknown account"
call 404 GET "$BASE_URL/accounts/00000000-0000-0000-0000-000000000000"
expect_field code ACCOUNT_NOT_FOUND

step "Invalid request: decimal amount"
call 400 POST "$BASE_URL/accounts/$BOB/deposits" '{"amount": 10.5}'
expect_field code INVALID_REQUEST

printf '\nSmoke test passed.\n'
