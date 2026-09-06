#!/usr/bin/env bash
# Removes every mock user (and their carts/cart items/Redis cart cache)
# created by provision-cart-users.sh. Safe to run even if nothing was
# provisioned - all deletes are scoped to the loadtest_cart_ email prefix.
#
# Usage: load-testing/postgres/cleanup-cart-users.sh

set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-junes-be-postgres-1}"
REDIS_CONTAINER="${REDIS_CONTAINER:-junes-be-redis-1}"
EMAIL_PATTERN="loadtest_cart_%@example.com"

user_ids=$(docker exec -i "$PG_CONTAINER" psql -U user -d junes -tAc \
  "SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';")

if [ -n "$user_ids" ]; then
  echo "Flushing Redis cart cache for $(echo "$user_ids" | wc -l) mock users ..."
  while IFS= read -r uid; do
    [ -z "$uid" ] && continue
    docker exec -i "$REDIS_CONTAINER" redis-cli DEL "user:cart:${uid}" >/dev/null
  done <<< "$user_ids"
fi

echo "Deleting mock users' Postgres rows (archived_carts, cart_items, carts, users) ..."

docker exec -i "$PG_CONTAINER" psql -U user -d junes <<SQL
DELETE FROM junes_rel.archived_carts
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.cart_items
  WHERE cart_id IN (
    SELECT cart_id FROM junes_rel.carts
    WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}')
  );

DELETE FROM junes_rel.carts
  WHERE user_id IN (SELECT user_id FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}');

DELETE FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';
SQL

remaining=$(docker exec -i "$PG_CONTAINER" psql -U user -d junes -tAc \
  "SELECT count(*) FROM junes_rel.users WHERE email LIKE '${EMAIL_PATTERN}';")

echo "Done. Remaining mock users matching pattern: ${remaining}"
