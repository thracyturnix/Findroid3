# Agent Notes

## Commit Invariant

Every commit to this fork must include a fork build version bump and keep the About
screen build note up to date.

- Bump `Versions.FORK_BUILD` in `buildSrc/src/main/kotlin/Versions.kt`.
- Include 2-3 short keywords after the build number that summarize the last
  change, because the phone About screen shows this under its build section.
- The phone About screen displays this value through `BuildConfig.FORK_BUILD`.
- Do not commit feature or fix changes without the corresponding fork build update.
- Run `scripts/install-git-hooks.sh` once after cloning. Local hooks block commits
  and pushes that omit the fork build bump or its 2-3 keyword description.

## README Ownership

Do not edit, stage, commit, or push `README.md`. The user owns all README changes
and will edit that file directly.

## Jellyfin Advisory Check

Before starting development work in a new session, run:

`scripts/check-jellyfin-upstream.sh`

Report any notices to the user before making changes. This check is advisory
only. Do not update the Jellyfin SDK, change compatibility behavior, implement
new Jellyfin capabilities, or update the stored review baseline without the
user's explicit approval.

## Upstream Maintenance

When updating this fork from `upstream/main`, preserve the fork features unless
the user explicitly says to drop one.

Before merging or rebasing:

- Fetch upstream and inspect `git log --oneline upstream/main..HEAD` and
  `git diff --name-only upstream/main..HEAD`.
- Treat these fork feature areas as intentional behavior:
  default start library, home library grid, watched/unwatched library actions,
  unplayed filtering, local episode watched-state persistence, current series
  item count requests, default MPV playback, display-vsync alignment, phone
  camera cutout playback avoidance, orange launcher branding, and the About
  build note.
- Expect conflicts around home/library UI, navigation, repository item queries,
  settings preferences, MPV playback, and `Versions.FORK_BUILD`.

After merging or rebasing:

- Re-check that the fork feature areas still compile and are still wired through
  their view models, repositories, settings, and UI entry points.
- Run at least `./gradlew :app:phone:assembleLibreDebug`.
- Bump `Versions.FORK_BUILD` with 2-3 keywords describing the upstream
  maintenance change before committing.
