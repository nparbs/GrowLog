# GrowLog Roadmap

Each milestone should leave the app better for daily use. The order puts protecting your data
first, then making the data useful, then reducing the effort of logging it.

## v0.1: Core tracking (done)

- [x] Plants with variety, stage, location, medium and start date; archive and delete
- [x] Per-plant, per-day log: water, pH, PPM, runoff, fertilizers, environment, height, notes
- [x] Backfilling and editing past days
- [x] Photos from camera and gallery, with captions
- [x] Today screen with one-tap watering
- [x] Reminders: once, every N days, weekly; notification Done and Snooze; survive reboots
- [x] Automated tests and CI that builds an APK on every push

## v0.2: Safe data and daily comfort (next)

- [ ] First on-device test pass (see Known limitations)
- [ ] **Full backup and restore** to a single file, photos included
- [ ] Export logs to CSV
- [ ] Settings: units (ml/L/gal/oz, °C/°F, cm/in) and default reminder time
- [ ] Copy yesterday's values into today's log
- [ ] Turn on R8 shrinking to cut the APK size, once the on-device pass is done

## v0.3: See the trends

- [ ] pH, PPM, runoff and environment charts per plant
- [ ] Target ranges per stage, flagging readings outside them
- [ ] Month calendar per plant showing watered, fed and photographed days
- [ ] Agenda view of upcoming reminders across all plants
- [ ] Stage history, showing days spent in each stage

## v0.4: Less tapping

- [ ] Group plants into grows or tents
- [ ] Log one watering or feed to a whole group at once
- [ ] Saved feed recipes, such as "Week 3 veg", that fill in fertilizers, pH and PPM
- [ ] Feeding schedules that generate reminders from a recipe plan
- [ ] Home screen widget for quick watering and what's due

## v0.5: Photos and review

- [ ] Photo timeline with grow day labels
- [ ] Side-by-side comparison of two dates
- [ ] Time-lapse video from a plant's photos
- [ ] Harvest record: yield, dates and notes
- [ ] End-of-grow summary and comparison between grows

## Later ideas

- Optional cloud sync between devices
- Bluetooth pH and PPM meter or environment sensor import
- Exact-time reminders as a setting
- Play Store release, which needs a real signing key

## Known limitations

- **Not yet run on a real device.** The app is covered by JVM tests that render real screens, but
  camera capture, the photo picker, delivered notifications and reboot behavior need a phone to
  confirm.
- **No user-facing backup yet.** Android's cloud backup covers logs and settings where it's enabled,
  but it deliberately leaves out photos to stay under its 25 MB limit. Uninstalling removes photos.
- **Units are metric only** until the v0.2 setting.
- **Reminders can arrive a few minutes late.** They use battery-friendly alarms, and aggressive
  battery savers on some phones may delay them further.
