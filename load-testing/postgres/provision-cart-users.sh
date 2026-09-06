#!/usr/bin/env bash
# Provisions mock, pre-verified users for the cart load tests
# (cart-session-throughput.js / cart-rate-limit-behavior.js).
#
# Registration normally requires clicking an emailed verification link before
# login works (AuthService.login rejects unverified accounts). Mailgun isn't
# reachable/mocked locally, so this script creates users through the real
# public API (correctly Argon2-hashed passwords) and then flips
# is_email_verified directly in Postgres - the one step that can't go
# through the public API in a load-test setting.
#
# All mock users share the email prefix below so cleanup-cart-users.sh can
# find and remove exactly what this script created, nothing else.
#
# Usage: load-testing/postgres/provision-cart-users.sh [count]
# Default count: 2 (lightweight local smoke test; pass a larger count for an
# EKS load-test run, matching CART_USERS passed to cart-session-throughput.js)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PG_CONTAINER="${PG_CONTAINER:-junes-be-postgres-1}"
EMAIL_PREFIX="loadtest_cart_"
EMAIL_DOMAIN="example.com"
PASSWORD="LoadTest1!"
COUNT="${1:-2}"

echo "Provisioning ${COUNT} mock cart-test users against ${BASE_URL} ..."

# Idempotent: clear out any leftover run before creating new ones.
"$(dirname "$0")/cleanup-cart-users.sh" >/dev/null 2>&1 || true

created=0
for i in $(seq 1 "$COUNT"); do
  email=$(printf "%s%03d@%s" "$EMAIL_PREFIX" "$i" "$EMAIL_DOMAIN")
  status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/junes/api/v1/auth/add-user" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${PASSWORD}\"}")
  if [ "$status" = "200" ]; then
    created=$((created + 1))
  else
    echo "  WARN: registration for ${email} returned HTTP ${status}" >&2
  fi
done

echo "Registered ${created}/${COUNT} users. Marking them email-verified in Postgres ..."

docker exec -i "$PG_CONTAINER" psql -U user -d junes -c \
  "UPDATE junes_rel.users SET is_email_verified = true WHERE email LIKE '${EMAIL_PREFIX}%@${EMAIL_DOMAIN}';"

verified=$(docker exec -i "$PG_CONTAINER" psql -U user -d junes -tAc \
  "SELECT count(*) FROM junes_rel.users WHERE email LIKE '${EMAIL_PREFIX}%@${EMAIL_DOMAIN}' AND is_email_verified = true;")

echo "Done. ${verified} mock users ready to log in (password: ${PASSWORD})."
