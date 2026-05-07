# Agent Notes

## Commit Invariant

Every commit to this fork must include a fork build version bump and keep the About
screen version note up to date.

- Bump `Versions.FORK_BUILD` in `buildSrc/src/main/kotlin/Versions.kt`.
- The phone About screen displays this value through `BuildConfig.FORK_BUILD`.
- Do not commit feature or fix changes without the corresponding fork build update.
