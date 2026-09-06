#!/usr/bin/env bash
# Removes every mock user (and their transactions/transaction_items/outbox
# events/idempotency keys/payment methods/addresses) created by
# provision-order-users.sh. Safe to run even if nothing was provisioned -
# all deletes are scoped to the loadtest_order_ email prefix.
#
# transactions.user_id has no ON DELETE CASCADE (unlike addresses/
# payment_methods), so transaction_items + transactions must be deleted
# before the users row, or the final DELETE fails on a foreign key.
#
# Usage: load-testing/postgres/cleanup-order-users.sh

set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-junes-be-postgres-1}"
EMAIL_PATTERN="loadtest_order_%@example.com"

echo "Deleting mock users' Postgres rows (outbox events, transactions, payment methods, addresses, users) ..."

docker exec -i "$PG_CONTAINER" psql -U user -d junes <<SQL
DELETE FROM junes_rel.outbox_events
  WHERE aggregate_id IN (
    SELECT order_number FROM junes_rel.transactions
    WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}')
  );

DELETE FROM junes_rel.transaction_items
  WHERE transaction_id IN (
    SELECT transaction_id FROM junes_rel.transactions
    WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}')
  );

DELETE FROM junes_rel.transactions
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.idempotency_keys
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.payment_methods
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.addresses
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';
SQL

remaining=$(docker exec -i "$PG_CONTAINER" psql -U user -d junes -tAc \
  "SELECT count(*) FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';")

echo "Done. Remaining mock users matching pattern: ${remaining}"
