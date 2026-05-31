#!/usr/bin/env bash
set -euo pipefail

range="${1:-}"
versions_file="buildSrc/src/main/kotlin/Versions.kt"

if [[ -z "$range" ]]; then
    echo "Usage: $0 <git-range>" >&2
    exit 1
fi

for commit in $(git rev-list --reverse "$range"); do
    parent="$(git rev-parse "$commit^" 2>/dev/null || true)"
    [[ -n "$parent" ]] || continue

    changed_files="$(git diff-tree --no-commit-id --name-only -r "$commit")"
    if [[ -z "$changed_files" || "$changed_files" == "README.md" ]]; then
        continue
    fi

    if ! grep -qx "$versions_file" <<<"$changed_files"; then
        echo "Fork build check failed: $commit does not update $versions_file." >&2
        exit 1
    fi

    previous_build="$(git show "$parent:$versions_file" | sed -n 's/^[[:space:]]*const val FORK_BUILD = "\(.*\)"/\1/p')"
    current_build="$(git show "$commit:$versions_file" | sed -n 's/^[[:space:]]*const val FORK_BUILD = "\(.*\)"/\1/p')"

    if [[ "$current_build" == "$previous_build" ]]; then
        echo "Fork build check failed: $commit does not change FORK_BUILD." >&2
        exit 1
    fi

    if [[ ! "$current_build" =~ ^\.[0-9]{3}[[:space:]][[:alnum:]-]+([[:space:]][[:alnum:]-]+){1,2}$ ]]; then
        echo "Fork build check failed: $commit has an invalid FORK_BUILD note." >&2
        exit 1
    fi
done
