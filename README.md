# GrowLog

An Android app for tracking indoor plant grows: daily watering, fertilizer, pH and PPM per plant,
with photos, notes and reminders. All data stays on the device; there's no account or server.

See [ROADMAP.md](ROADMAP.md) for what's done and what's next.

## Features

- **Today:** every plant at a glance, what's due now, and one-tap watering.
- **Daily log, per plant and per day:** water amount, feed pH and PPM, runoff pH and PPM,
  any number of fertilizers, temperature, humidity, height, light hours and notes. Past days can
  be filled in or edited.
- **Photos:** from the camera or gallery, attached to a day, with captions.
- **Plants:** variety, stage, location, medium, start date and grow day count. Finished plants can
  be archived with their history intact.
- **Reminders:** one-off, every N days or on chosen weekdays, for one plant or all of them. Each
  notification has Done and Snooze buttons. Completing a watering reminder also logs the watering.

## Install

Download the APK from the [latest release](https://github.com/nparbs/GrowLog/releases/latest) on
your phone and open it, allowing installs from that app if Android asks. Requires Android 8.0+.
Newer versions install over the old one and keep your data.

## Build

Requires JDK 17 and the Android SDK (API 36). Set `sdk.dir` in `local.properties`, then:

```sh
./gradlew testDebugUnitTest   # tests, also writes screenshots to app/build/screenshots
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Every push to `main` runs the same steps in GitHub Actions and uploads the APK as an artifact.
That APK is signed with a throwaway key, so it can't update a copy from the releases page.

If your home folder path contains a space, Robolectric can't find its jars. Point the tests at a
path without one, such as the Windows short name:

```sh
ROBOLECTRIC_USER_HOME='C:/Users/NPFFCD~1' ./gradlew testDebugUnitTest
```

## Code layout

Kotlin, Jetpack Compose, Room and AlarmManager, under `app/src/main/java/com/nparbs/growlog`:

| Folder | Contents |
| --- | --- |
| `data/` | Room entities and DAOs, photo storage, and `GrowRepository`, which all screens go through |
| `reminders/` | Schedule rules (pure logic), alarms, notifications and receivers |
| `ui/` | Compose screens, each with its ViewModel in the same file |

Measurements are stored in metric units, so a display-unit setting won't need a data migration.
The schema is exported to `app/schemas`. Any schema change needs a Room migration, because the
database is never rebuilt destructively.

Release builds are signed with `app/debug.keystore` when that file exists, and with Gradle's
default debug key otherwise. Keystores are ignored by git. Use a new key for any Play Store release.
