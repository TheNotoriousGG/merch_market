#!/bin/sh

set -eu

baseline_directory="$1"
contract_path="src/main/openapi"

rm -rf "$baseline_directory"
mkdir -p "$baseline_directory/$contract_path"

if git cat-file -e "main:$contract_path/openapi.yaml" 2>/dev/null; then
    git ls-tree -r --name-only main "$contract_path" | while IFS= read -r repository_path; do
        destination="$baseline_directory/$repository_path"
        mkdir -p "$(dirname "$destination")"
        git show "main:$repository_path" > "$destination"
    done
else
    echo "No OpenAPI contract exists on main yet; comparing the first adoption with itself."
    cp -R "$contract_path/." "$baseline_directory/$contract_path/"
fi
