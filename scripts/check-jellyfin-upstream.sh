#!/usr/bin/env bash
set -euo pipefail

baseline_file="${1:-docs/jellyfin-upstream-baseline.json}"
versions_file="gradle/libs.versions.toml"
server_releases_url="https://api.github.com/repos/jellyfin/jellyfin/releases/latest"
sdk_metadata_url="https://repo1.maven.org/maven2/org/jellyfin/sdk/jellyfin-core/maven-metadata.xml"

if [[ ! -f "$baseline_file" ]]; then
    echo "Jellyfin advisory check failed: missing $baseline_file." >&2
    exit 1
fi

current_sdk="$(
    sed -n 's/^jellyfin = "\([^"]*\)"/\1/p' "$versions_file" |
        head -n 1
)"
reviewed_server="$(
    sed -n 's/.*"reviewed_server_release":[[:space:]]*"\([^"]*\)".*/\1/p' "$baseline_file"
)"
reviewed_sdk="$(
    sed -n 's/.*"reviewed_sdk_release":[[:space:]]*"\([^"]*\)".*/\1/p' "$baseline_file"
)"

latest_server="$(
    curl -fsSL "$server_releases_url" |
        sed -n 's/.*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' |
        head -n 1
)"
latest_sdk="$(
    curl -fsSL "$sdk_metadata_url" |
        sed -n 's:.*<release>\(.*\)</release>.*:\1:p' |
        head -n 1
)"

if [[ -z "$current_sdk" || -z "$latest_server" || -z "$latest_sdk" ]]; then
    echo "Jellyfin advisory check failed: could not read version information." >&2
    exit 1
fi

echo "Jellyfin advisory report"
echo "  Findroid SDK:        $current_sdk"
echo "  Latest SDK:          $latest_sdk"
echo "  Latest server:       $latest_server"
echo "  Reviewed SDK:        ${reviewed_sdk:-not recorded}"
echo "  Reviewed server:     ${reviewed_server:-not recorded}"
echo

has_notice=false

if [[ "$current_sdk" != "$latest_sdk" ]]; then
    echo "NOTICE: Findroid's Jellyfin SDK differs from the latest published SDK."
    has_notice=true
fi

if [[ "$reviewed_sdk" != "$latest_sdk" ]]; then
    echo "NOTICE: The latest Jellyfin SDK release has not been recorded as reviewed."
    has_notice=true
fi

if [[ "$reviewed_server" != "$latest_server" ]]; then
    echo "NOTICE: The latest Jellyfin Server release has not been recorded as reviewed."
    has_notice=true
fi

if [[ "$has_notice" == false ]]; then
    echo "No Jellyfin release changes require review."
fi

echo
echo "Advisory only: do not update dependencies or app behavior without explicit user approval."
