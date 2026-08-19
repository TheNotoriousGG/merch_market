#!/bin/sh

set -eu

fail() {
    printf 'supply-chain policy violation: %s\n' "$1" >&2
    exit 1
}

if grep '^FROM ' Dockerfile | grep -Ev '@sha256:[0-9a-f]{64}([[:space:]]|$)' >/dev/null; then
    fail "every Dockerfile base image must be pinned by digest"
fi

fixed_image_count="$(grep -Ec 'name: "(gradle:|moby/|curlimages/|hadolint/)' .gitlab-ci.yml)"
[ "$fixed_image_count" -eq 4 ] || fail "expected four explicitly pinned CI tool images"

grep -E 'name: "(gradle:|moby/|curlimages/|hadolint/)' .gitlab-ci.yml |
    while IFS= read -r image_declaration; do
        printf '%s\n' "$image_declaration" | grep -Eq '@sha256:[0-9a-f]{64}"$' ||
            fail "CI tool image is not pinned by digest"
    done

grep -Fq 'USER nonroot:nonroot' Dockerfile || fail "runtime image must use distroless nonroot identity"
grep -Fq 'BUILDKITD_FLAGS: "--oci-worker-no-process-sandbox"' .gitlab-ci.yml ||
    fail "container build must use the reviewed rootless BuildKit mode"
grep -Fq -- '--opt attest:provenance=mode=max' .gitlab-ci.yml || fail "SLSA provenance is required"
grep -Fq -- '--opt attest:sbom=' .gitlab-ci.yml || fail "container SBOM is required"
grep -Fq 'CS_IMAGE: "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA"' .gitlab-ci.yml ||
    fail "container scanning must target the commit-addressed image"
grep -Fq 'name: "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA"' .gitlab-ci.yml ||
    fail "smoke test must start the commit-addressed image"

if grep -Fq 'allow_failure: true' .gitlab-ci.yml; then
    fail "repository-owned blocking jobs cannot allow failure"
fi

printf 'supply-chain policy checks passed\n'
