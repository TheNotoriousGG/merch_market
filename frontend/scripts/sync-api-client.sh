#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
frontend_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
backend_dir=${AMRA_BACKEND_DIR:-"$frontend_dir/../backend"}
artifact="$backend_dir/build/distributions/amra-shop-api-client-0.1.0.zip"
spec="$backend_dir/src/main/openapi/openapi.yaml"
target="$frontend_dir/app/api/generated"
temp_dir=$(mktemp -d "${TMPDIR:-/tmp}/amra-api-client.XXXXXX")
trap 'rm -rf "$temp_dir"' EXIT

"$backend_dir/gradlew" -p "$backend_dir" packageTypeScriptClient --no-daemon
unzip -q "$artifact" -d "$temp_dir"
rm -rf "$target"
mkdir -p "$target"
cp -R "$temp_dir/src/." "$target/"
find "$target" -type f -name '*.ts' -exec perl -0777 -pi -e 's/[ \t]+$//mg; s/\s+\z/\n/' {} +

spec_sha=$(shasum -a 256 "$spec" | awk '{print $1}')
artifact_sha=$(shasum -a 256 "$artifact" | awk '{print $1}')
api_version=$(sed -n 's/^  version: //p' "$spec" | head -1)
backend_commit=$(git -C "$backend_dir" rev-parse HEAD)

cat > "$frontend_dir/app/api/generated-manifest.json" <<EOF
{
  "apiVersion": "$api_version",
  "specSha256": "$spec_sha",
  "artifactSha256": "$artifact_sha",
  "backendCommit": "$backend_commit"
}
EOF

echo "Synchronized @amra-shop/api-client $api_version ($spec_sha)"
