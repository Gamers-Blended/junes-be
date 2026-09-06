#!/usr/bin/env bash
# Removes every account created by auth-registration-throughput.js's
# "registrations" scenario (prefix loadtest_authreg_). These accounts are
# never verified (that's not what the test exercises), so unlike
# cleanup-cart-users.sh/cleanup-order-users.sh there's no cart/address/
# payment-method/transaction data to worry about - just the users row and
# whatever email-verification tokens were minted for it.
#
# Usage: load-testing/postgres/cleanup-auth-reg-users.sh

set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-junes-be-postgres-1}"
EMAIL_PATTERN="loadtest_authreg_%@example.com"

docker exec -i "$PG_CONTAINER" psql -U user -d junes <<SQL
DELETE FROM junes_rel.email_verification_tokens
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';
SQL

remaining=$(docker exec -i "$PG_CONTAINER" psql -U user -d junes -tAc \
  "SELECT count(*) FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';")

echo "Done. Remaining mock users matching pattern: ${remaining}"
