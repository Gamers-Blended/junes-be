#!/usr/bin/env bash
# Provisions mock, pre-verified users for the order/checkout load tests
# (order-creation-throughput.js / order-idempotency-race.js), each with one
# saved address and one saved payment method already in Postgres.
#
# Same registration-then-verify bypass as provision-cart-users.sh (Mailgun
# isn't reachable locally). Address/payment-method rows are inserted
# directly via SQL rather than through SavedItemsController, because
# POST /saved-items/payment-method calls real Stripe to validate the
# PaymentMethod ID before saving - unnecessary for this test, since
# OrderController's POST /order/place never touches Stripe synchronously
# (it writes a PAYMENT_PENDING transaction + outbox event; the actual
# charge happens later, async, off a Kafka consumer). The stripe_customer_id/
# stripe_payment_method_id columns are left as obvious stub values -
# fine for exercising the HTTP request path, but the async charge for
# these orders will fail against real Stripe if a Kafka consumer is running.
#
# All mock users share the email prefix below so cleanup-order-users.sh can
# find and remove exactly what this script created, nothing else.
#
# Usage: load-testing/postgres/provision-order-users.sh [count]
# Default count: 2 (lightweight local smoke test; pass a larger count for an
# EKS load-test run, matching ORDER_USERS passed to order-creation-throughput.js)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PG_CONTAINER="${PG_CONTAINER:-junes-be-postgres-1}"
EMAIL_PREFIX="loadtest_order_"
EMAIL_DOMAIN="example.com"
PASSWORD="LoadTest1!"
COUNT="${1:-2}"

echo "Provisioning ${COUNT} mock order-test users against ${BASE_URL} ..."

# Idempotent: clear out any leftover run before creating new ones.
"$(dirname "$0")/cleanup-order-users.sh" >/dev/null 2>&1 || true

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

echo "Registered ${created}/${COUNT} users. Verifying them + seeding address/payment method ..."

docker exec -i "$PG_CONTAINER" psql -U user -d junes <<SQL
UPDATE junes_rel.users SET is_email_verified = true
  WHERE email LIKE '${EMAIL_PREFIX}%@${EMAIL_DOMAIN}';

-- created_on has no DB-level DEFAULT - Hibernate's @CreationTimestamp sets
-- it in-app on every entity save, so raw SQL inserts must supply it.
INSERT INTO junes_rel.addresses
  (address_id, full_name, address_line, country, zip_code, phone_number, is_default, user_id, created_on)
SELECT gen_random_uuid(), 'Load Test User', '123 Load Test St', 'USA', '00000', '+10000000000', true, user_id, NOW()
FROM junes_rel.users
WHERE email LIKE '${EMAIL_PREFIX}%@${EMAIL_DOMAIN}';

INSERT INTO junes_rel.payment_methods
  (payment_method_id, card_type, card_last_four, card_holder_name, expiration_month, expiration_year,
   billing_address_id, user_id, is_default, is_active, card_fingerprint, stripe_customer_id, stripe_payment_method_id, created_on)
SELECT gen_random_uuid(), 'visa', '4242', 'Load Test User', '12', to_char(NOW() + INTERVAL '2 years', 'YYYY'),
       a.address_id, u.user_id, true, true, md5(u.user_id::text), 'cus_loadtest_stub', 'pm_loadtest_stub', NOW()
FROM junes_rel.users u
JOIN junes_rel.addresses a ON a.user_id = u.user_id
WHERE u.email LIKE '${EMAIL_PREFIX}%@${EMAIL_DOMAIN}';
SQL

ready=$(docker exec -i "$PG_CONTAINER" psql -U user -d junes -tAc \
  "SELECT count(*) FROM junes_rel.users u
   JOIN junes_rel.addresses a ON a.user_id = u.user_id
   JOIN junes_rel.payment_methods p ON p.user_id = u.user_id
   WHERE u.email LIKE '${EMAIL_PREFIX}%@${EMAIL_DOMAIN}' AND u.is_email_verified = true;")

echo "Done. ${ready} mock users ready to check out (password: ${PASSWORD})."
