#!/usr/bin/env bash
set -euo pipefail

readonly ARCHIVE_PATH="${1:?TypeScript client archive path is required}"
readonly EXPECTED_VERSION="${2:?Expected client version is required}"
readonly ARCHIVE_ENTRIES="$(unzip -Z1 "${ARCHIVE_PATH}")"

require_entry() {
  local entry="$1"
  if ! grep -Fqx "${entry}" <<<"${ARCHIVE_ENTRIES}"; then
    echo "Generated TypeScript client is missing ${entry}" >&2
    exit 1
  fi
}

for entry in \
  package.json \
  src/runtime.ts \
  src/index.ts \
  src/apis/CatalogApi.ts \
  src/apis/CatalogAdministrationApi.ts \
  src/apis/InventoryApi.ts \
  src/apis/InventoryAdministrationApi.ts \
  src/models/InventoryAvailabilityList.ts \
  src/models/InventoryMutationResult.ts \
  src/models/ProblemDetails.ts; do
  require_entry "${entry}"
done

if grep -Eq '^\.openapi-generator($|/)|^\.openapi-generator-ignore$' <<<"${ARCHIVE_ENTRIES}"; then
  echo "Generated TypeScript client contains generator bookkeeping" >&2
  exit 1
fi

readonly PACKAGE_JSON="$(unzip -p "${ARCHIVE_PATH}" package.json)"
grep -Fq '"name": "@amra-shop/api-client"' <<<"${PACKAGE_JSON}"
grep -Fq "\"version\": \"${EXPECTED_VERSION}\"" <<<"${PACKAGE_JSON}"
if grep -Eq 'GIT_USER_ID|GIT_REPO_ID' <<<"${PACKAGE_JSON}"; then
  echo "Generated TypeScript client contains unresolved repository placeholders" >&2
  exit 1
fi

readonly CATALOG_API="$(unzip -p "${ARCHIVE_PATH}" src/apis/CatalogApi.ts)"
for operation in getCatalogCategories getCatalogProduct getCatalogProducts; do
  grep -Fq "${operation}" <<<"${CATALOG_API}"
done

readonly ADMINISTRATION_API="$(unzip -p "${ARCHIVE_PATH}" src/apis/CatalogAdministrationApi.ts)"
for operation in \
  createCatalogCategory \
  createCatalogCollection \
  createCatalogProduct \
  createCatalogProductMedia \
  createCatalogProductVariant \
  transitionCatalogProduct \
  updateCatalogCollectionProducts; do
  grep -Fq "${operation}" <<<"${ADMINISTRATION_API}"
done

readonly INVENTORY_API="$(unzip -p "${ARCHIVE_PATH}" src/apis/InventoryApi.ts)"
grep -Fq 'getInventoryAvailability' <<<"${INVENTORY_API}"

readonly INVENTORY_ADMINISTRATION_API="$(unzip -p "${ARCHIVE_PATH}" src/apis/InventoryAdministrationApi.ts)"
for operation in adjustInventoryStock getAdminInventoryBalance receiveInventoryStock; do
  grep -Fq "${operation}" <<<"${INVENTORY_ADMINISTRATION_API}"
done

echo "Verified TypeScript client artifact ${ARCHIVE_PATH}"
