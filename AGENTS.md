# Agent Notes

## Commit Invariant

Every commit to this fork must include a fork build version bump and keep the About
screen build note up to date.

- Bump `Versions.FORK_BUILD` in `buildSrc/src/main/kotlin/Versions.kt`.
- Include 2-3 short keywords after the build number that summarize the last
  change, because the phone About screen shows this under its build section.
- The phone About screen displays this value through `BuildConfig.FORK_BUILD`.
- Do not commit feature or fix changes without the corresponding fork build update.
