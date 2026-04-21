![Findroid banner](images/findroid-banner.png)

# Findroid3

Findroid3 is a private fork of [Findroid](https://github.com/jarnedemeulemeester/findroid), a third-party Android application for Jellyfin that provides a native user interface to browse and play movies and series.

This fork contains custom changes that are not present in upstream. It should be treated as a modified branch of the original project, not as the canonical upstream repository.

## Fork Notes
- Renamed app branding to `Findroid3`.
- Added capacity to "Hide Watched" in libraries
- Toggle Hide Watched on and off for each library
- Select individual items (long press) to mark as watched or unwatched within any library
- Added fork-specific phone UI changes, including a top home-screen libraries matrix for quick selection.
- Reconfigured home screen
- Enabled "start as default" feature so app will start in specified library (instead of Home) 
- Should be kept updated against upstream as needed, but may intentionally diverge where the fork requires different behavior.

## Upstream
The upstream project remains [jarnedemeulemeester/findroid](https://github.com/jarnedemeulemeester/findroid). Upstream documentation, release information, and community links may not match the exact state of this fork.

## Screenshots
| Home                                | Library                             | Movie                           | Season                            | Episode                             |
|-------------------------------------|-------------------------------------|---------------------------------|-----------------------------------|-------------------------------------|
| ![Home](fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png) | ![Library](fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png) | ![Movie](fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png) | ![Season](fastlane/metadata/android/en-US/images/phoneScreenshots/4_en-US.png) | ![Episode](fastlane/metadata/android/en-US/images/phoneScreenshots/5_en-US.png) |

## Features
- Completely native interface
- Supported media items: movies, series, seasons, episodes 
  - Direct play only, (no transcoding)
- Offline playback / downloads
- ExoPlayer
  - Video codecs: H.263, H.264, H.265, VP8, VP9, AV1 
    - Support depends on Android device
  - Audio codecs: Vorbis, Opus, FLAC, ALAC, PCM, MP3, AAC, AC-3, E-AC-3, DTS, DTS-HD, TrueHD 
    - Support provided by ExoPlayer FFmpeg extension
  - Subtitle codecs: SRT, VTT, SSA/ASS, PGSSUB
    - SSA/ASS has limited styling support see [this issue](https://github.com/google/ExoPlayer/issues/8435)
- mpv
  - Container formats: mkv, mov, mp4, avi
  - Video codecs: H.264, H.265, H.266, VP8, VP9, AV1
  - Audio codecs: Opus, FLAC, MP3, AAC, AC-3, E-AC-3, TrueHD, DTS, DTS-HD
  - Subtitle codecs: SRT, VTT, SSA/ASS, DVDSUB
  - Optionally force software decoding when hardware decoding has issues.
- Picture-in-picture mode
- Media chapters
  - Timeline markers
  - Chapter navigation gestures
- Trickplay (requires Jellyfin 10.9 or higher)
- Media segments (requires Jellyfin 10.10 or higher)
  - Skip button
  - Auto skip

## Planned features
- Android TV
- Websocket connection (Syncplay)
- Chromecast support

## Translating
[JDTech Weblate](https://weblate.jdtech.dev) is a self-hosted instance of Weblate where you can translate this project and future projects of mine.

## Questions?
We have a [Discord server](https://discord.gg/tg5VvTFwTV) to discuss future development or ask general questions.

## License
This project is licensed under [GPLv3](LICENSE).

The logo is a combination of the Jellyfin logo and the Android robot.

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.

Android is a trademark of Google LLC.

Google Play and the Google Play logo are trademarks of Google LLC.
