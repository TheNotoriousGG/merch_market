#!/bin/sh

set -eu

: "${AMRA_MIGRATION_PASSWORD:?AMRA_MIGRATION_PASSWORD is required}"
: "${AMRA_RUNTIME_PASSWORD:?AMRA_RUNTIME_PASSWORD is required}"

psql \
    --set=ON_ERROR_STOP=1 \
    --set=migration_password="$AMRA_MIGRATION_PASSWORD" \
    --set=runtime_password="$AMRA_RUNTIME_PASSWORD" \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" <<'SQL'
REVOKE ALL ON DATABASE amra_market FROM PUBLIC;
GRANT CONNECT ON DATABASE amra_market TO amra_owner;

CREATE ROLE amra_migrator LOGIN PASSWORD :'migration_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE amra_runtime LOGIN PASSWORD :'runtime_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

GRANT CONNECT ON DATABASE amra_market TO amra_migrator, amra_runtime;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA amra_shop AUTHORIZATION amra_owner;
COMMENT ON SCHEMA amra_shop IS 'Application-owned schema for Amra Merch Market';
GRANT USAGE, CREATE ON SCHEMA amra_shop TO amra_migrator;
GRANT USAGE ON SCHEMA amra_shop TO amra_runtime;

ALTER ROLE amra_migrator IN DATABASE amra_market SET search_path TO amra_shop, pg_catalog;
ALTER ROLE amra_migrator IN DATABASE amra_market SET statement_timeout TO '10min';
ALTER ROLE amra_runtime IN DATABASE amra_market SET search_path TO amra_shop, pg_catalog;
ALTER ROLE amra_runtime IN DATABASE amra_market SET statement_timeout TO '2s';
ALTER ROLE amra_runtime IN DATABASE amra_market SET lock_timeout TO '1s';
ALTER ROLE amra_runtime IN DATABASE amra_market SET idle_in_transaction_session_timeout TO '10s';

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
SQL
