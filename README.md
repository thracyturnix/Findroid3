![Findroid banner](images/findroid-banner.png)

# Findroid3

Findroid3 is a fork of [Findroid](https://github.com/jarnedemeulemeester/findroid), a third-party Android application for Jellyfin that provides a native user interface to browse and play movies and series.

This fork contains custom changes that are not present in upstream. It should be treated as a modified branch of the original project, not as the canonical upstream repository.

## Fork Notes
- Added capacity to "Hide Watched" in libraries
- Select individual items (long press) to mark as watched or unwatched within any library
- Phone UI changes, including a top home-screen libraries matrix for quick selection.
- Enabled "start as default" feature so app will start every time in preferred library (instead of Home) 
- MPV set to default
- Display mod to bypass 'hole' of the camera
- Fixed watched-state persistence (broken)

## Upstream
The upstream project remains [jarnedemeulemeester/findroid](https://github.com/jarnedemeulemeester/findroid). Upstream documentation, release information, and community links may not match the exact state of this fork.

## Screenshots
| Home                                | Library                             | Movie                           | Season                            | Episode                             |
|-------------------------------------|-------------------------------------|---------------------------------|-----------------------------------|-------------------------------------|
| ![Home](fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png) | ![Library](fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png) | ![Movie](fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png) | ![Season](fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png) | ![Episode](fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png) |

## License
This project is licensed under [GPLv3](LICENSE).

The logo is a combination of the Jellyfin logo and the Android robot.

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.

Android is a trademark of Google LLC.

Google Play and the Google Play logo are trademarks of Google LLC.
