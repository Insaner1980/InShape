# InShape music player verification

18 November 2026

## Reference comparison

The API 36 emulator captures use English, Dark appearance, 390 dp width, 100% font scale and a 780 × 1736 portrait viewport. Each capture was compared with its [original reference](../../references/music-player/) at equal width.

| Screen | Capture | Comparison |
| --- | --- | --- |
| Music home | [Music home](music-home.png) | The header, segmented control, three covers, separate play targets and blank lower area follow the reference. |
| Playlist detail | [Playlist detail](playlist-detail.png) | Music remains selected and one back arrow is visible. The list includes the remaining tracks from the twelve-song fixture. |
| Active workout | [Workout mini-player](workout-mini-player.png) | The mini-player is directly above Exercise done. The emulator's taller system bar and the existing workout spacing leave part of set three and Add set below the viewport. Both are reachable by scrolling. |

On the reference emulator the artwork and controls sit lower on the canvas because the native status bar is taller. At 200% font scale the heading action and playlist controls can stack, and the workout player moves into scrollable content.

## Checks

| Check | Observed result |
| --- | --- |
| Gradle | Debug app and instrumentation APK assembled. All 111 JVM tests passed. Android lint reported zero errors and two existing warnings for a version suggestion and the backup free-space check. |
| Merged release manifest | The service declares `mediaPlayback` and foreground service permissions. The app requests folder access through SAF and the release manifest contains no Internet, network state or broad audio library permission. |
| Dependency integrity | Verified 22 new artifact checksums against Google Maven. Gradle verification passed with the checked-in metadata. |
| API 36 Compose layout | Both MusicUiTest cases passed in each of eight configurations. Widths were 390 and 320 dp, font scales were 100% and 200%, and each pair ran in Dark and Light. The cases exercised the dedicated play target, detail and Back, tab re-entry, and changing or cancelling music in a workout. |
| API 36 playback and persistence | Both MusicPlaybackTest cases passed. The Android folder picker supplied the source, including a subfolder. MP3, AAC in M4A, FLAC, 16-bit PCM WAV, Vorbis in Ogg and Opus in Ogg reported duration, advanced and sought. The tests covered rapid queue replacements and Play next with shuffle, the repeat toggle, unreadable-track recovery, ordered playlist edits and a version 1 journal restore with playlists and the queue present. |
| API 26 playback and persistence | Both MusicPlaybackTest cases passed after selecting the same fixture folder through the Android 8 picker. All six formats advanced and sought. The InShape session stayed in PLAYING and its media notification remained present while Android Settings was open. |
| API 36 journal and navigation regression | NavigationTest, EndToEndJournalTest, WorkoutTimingEditorTest and RedesignInteractionTest passed together with 36 tests. |
| API 36 real workout | Started a playlist from the overflow of an empty active workout, paused and skipped, then changed playlists through the mini-player. Added an exercise, entered 27.5 kg and 10 reps, completed the set and switched playlists again. Both footer rows hid while the set keyboard was open. The saved workout showed exactly one set with the entered values, and playback continued after saving. Numeric editing at 320 dp and 200% font scale also opened the keyboard and hid the fixed workout action. |
| API 36 notifications and restart | Both the media and workout notifications appeared together on distinct channels. The media session stayed in PLAYING while the emulator was asleep and responded to the platform pause key after waking. A forced process stop and relaunch restored the queue and the same 5.575-second position without starting audio. |
| API 36 folder recovery | Cancelling the picker preserved the existing songs. A refresh after removing an indexed MP3 retained its playlist membership and displayed it as unavailable. Restoring the file and refreshing made the song available again. Choosing an empty folder showed the empty Songs state and retained the saved playlists. Selecting the original tree recovered its songs. A reinstall that kept app data and removed the URI grant retained both playlists and exposed Choose folder again. |

Instrumentation targeted disposable emulator serials with host audio disabled. Decoding, play state and position were checked in the Media3 session. The connected phone was reserved for another app. Physical audio output, Bluetooth and audio-focus interruption, and a complete TalkBack walkthrough including spoken output remain unverified.
