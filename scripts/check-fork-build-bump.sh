#!/usr/bin/env bash
set -euo pipefail

versions_file="buildSrc/src/main/kotlin/Versions.kt"

if ! git diff --cached --quiet -- . ':!README.md'; then
    if git diff --cached --quiet -- "$versions_file"; then
        echo "Commit blocked: bump Versions.FORK_BUILD in $versions_file." >&2
        echo "Include 2-3 short keywords describing the change." >&2
        exit 1
    fi
fi

fork_build_line="$(git show ":$versions_file" | sed -n 's/^[[:space:]]*const val FORK_BUILD = "\(.*\)"/\1/p')"

if [[ ! "$fork_build_line" =~ ^\.[0-9]{3}[[:space:]][[:alnum:]-]+([[:space:]][[:alnum:]-]+){1,2}$ ]]; then
    echo "Commit blocked: FORK_BUILD must look like '.011 subtitle episode checks'." >&2
    exit 1
fi
