# Tummy Talk

A private, offline Android app for logging fetal movements with one tap from a
permanently pinned notification.

Package id stays `com.omi.kickcounter` — only the display name is "Tummy Talk".

## What it does

- An ongoing, silent notification stays in the shade at all times — after a
  reboot too. Its **Baby moved** button records a movement without opening the
  app; **Undo** removes the last one. A short vibration confirms each tap.
- If she gives the baby a name in Settings, that button reads **"Aria moved"**
  instead. The name is capped at 14 characters so the button never overflows.
- The notification cannot be cleared. `setOngoing` blocks the swipe on older
  releases, and because Android 13 and up allow a foreground-service notification
  to be swiped away (or caught by "Clear all") while the service keeps running, a
  delete intent re-posts it immediately. The 30-second refresh is a second safety
  net.
- The notification line shows gestational age, today's total, this hour's total
  and how long ago the last movement was.
- The app itself shows gestational age against the 40-week timeline, a large tap
  dial with the count-to-ten session ring, hourly bars for today, daily totals for
  the last 14 days, and a day-by-day table with each day's time to goal.
- Everything is stored on the device. The app declares **no `INTERNET`
  permission**, so the log physically cannot be uploaded anywhere. A CSV export is
  available for sharing with a clinician.

## Dates

Gestational age is measured from the first day of the last menstrual period.
The app ships with **LMP = 2026-02-22**, which makes 2026-08-09 exactly 24w 0d and
puts the estimated due date at **2026-11-29**. Both are editable under
Settings → Pregnancy dates.

If the clinic gave a scan-adjusted due date, set the LMP to that due date minus
280 days.

## Hourly reminder

On the hour, inside a waking window she configures (default 9 AM to 9 PM), the app
checks whether anything was logged in the hour that just ended. If not, it sounds a
reminder on its own high-priority channel — the persistent counter channel stays
silent forever. The wording is deliberately a nudge, not a warning: a quiet hour is
normal, and the reminder exists only in case logging was forgotten.

Alarms are scheduled one at a time with `AlarmManager` and re-armed on fire, on
boot, on app update and on a clock or timezone change. Exact delivery is used when
permitted and inexact otherwise, since an hourly nudge that slips a few minutes is
still useful. The receiver re-checks the waking window before making any sound,
because a snooze or a clock change can otherwise deliver an alarm outside it.

## Counting model

Modelled on how clinical guidance actually describes counting: a timed count to ten,
not a daily tally.

- She just taps for each kick, roll, jab or swish — the count **starts itself** on
  the first movement, so the notification alone is enough. Hiccups are excluded;
  they are involuntary.
- The session's useful output is **time to reach the goal** (ten movements by
  default). It closes automatically on reaching the goal, and expires after two
  hours, which is the window ACOG frames the target against. There is no "end"
  button: auto-start, auto-close and the two-hour expiry cover every case, and a
  third button under her thumb only invites mis-taps.
- Taps outside a session are still recorded and still count towards the daily
  total; they just are not part of a timed session.
- **Busiest 2 hours** is shown alongside, as a sliding window over the whole day. A
  session anchored at the first tap can expire below the goal simply because it
  began during a quiet spell; this figure is what stops that looking alarming. A
  count that ends below the goal is never styled as a warning.
- Every tap is stored raw and is never rewritten. Optional grouping
  (Settings → Counting, off by default) treats taps within 1/2/5 minutes as one
  movement, applied at read time, so it can be changed without losing data.
- Before 28 weeks the home screen says plainly that the numbers do not mean much
  yet.

The app is a memory aid and a record for a clinician — not a test of the baby's
wellbeing. Current RCOG guidance emphasises noticing a *change* from normal over
hitting any number.

## Undo and redo

Undo does not delete a row, it stamps `deletedAt`. That keeps the original
timestamp so **redo restores the movement at the moment she felt it**, not the
moment she noticed the mistake. Both are available for 30 minutes after the undo;
without an expiry the button would sit enabled for days and could resurrect a tap
into a long-past day.

Undoing the movement that *opened* a count also deletes that now-empty session,
rather than leaving it reading "0 of 10".

The redo action appears as a third notification button only while an undo is fresh.

## Data and migrations

Room, at schema version 3.

| Version | Change |
|---|---|
| 1 | `kicks` |
| 2 | added `sessions` |
| 3 | added `kicks.deletedAt`, making undo reversible |

Migration DDL lives in `DatabaseSchema.kt`, deliberately free of Android types so
`MigrationTest` can run the real upgrade against SQLite on the JVM and assert that
existing taps survive. A further test fails the build if that DDL ever drifts from
the schema Room exports to `app/schemas/`.

Updates preserve everything. Only uninstalling or **Settings → Apps → Tummy Talk →
Storage → Clear data** erases her log. Note that `allowBackup` is on, so Android may
auto-restore an old backup on the next install — uninstalling is not a reliable wipe.

## Build and install

Requires JDK 17+ and the Android SDK (platform 35). `local.properties` points at
the SDK.

```
gradlew.bat :app:testDebugUnitTest     # unit tests
gradlew.bat :app:assembleRelease       # APK
adb install -r --user 0 app\build\outputs\apk\release\app-release.apk
```

**Use `--user 0`.** Plain `adb install` targets every user profile, and on a phone
with Samsung Dual App enabled that silently creates a second icon with its own
empty database. To remove one that already exists:
`adb shell pm uninstall --user 95 com.omi.kickcounter`.

The release build is signed with the local debug key — fine for sideloading to a
single phone, not for distribution. It is therefore not debuggable, so `run-as`
cannot read the database; back-ups have to go through the in-app CSV export.

WhatsApp blocks `.apk` attachments, so `dist/` also carries a `.apk.zip` copy to
send; it is byte-identical and gets renamed back on the phone.

## After installing

1. Allow the notification permission when prompted (Android 13+).
2. **Expand the notification once** — One UI shows it collapsed, which hides the
   action buttons. It stays expanded afterwards.
3. Grant exact alarms: Settings → **Allow exact alarms**, or
   `adb shell appops set com.omi.kickcounter SCHEDULE_EXACT_ALARM allow`. It is
   denied by default, and without it reminders drift by up to an hour.
4. On Samsung, check **Settings → Battery → Background usage limits** and make sure
   the app is in neither *Sleeping apps* nor *Deep sleeping apps*, then set
   **Apps → Tummy Talk → Battery → Unrestricted**. Other OEMs (Xiaomi, Oppo, Vivo)
   need the equivalent autostart and battery exemptions.

## Layout

```
domain/    gestational age, bucketing, session and window logic — pure, unit tested
data/      Room tables for taps and sessions, DataStore settings, migration DDL
reminder/  hourly alarm scheduling and the waking-window check
service/   foreground service, notification actions, boot restart
ui/        Compose screens, theme, chart and dial components
```

## Verified on device

Samsung Galaxy M35 5G (SM-M356B), Android 16. Foreground service type
`SPECIAL_USE`; counter channel silent with `sound=null`; reminder channel
`importance=4` with sound; alarm scheduled exactly on the hour and correctly
deferred overnight; gestational age, dial, ring, session counting, undo/redo and
the notification labels all confirmed against `dumpsys`.
