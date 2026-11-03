# CoC Timers — Project Guide

This document is the end-to-end handoff guide for a developer or maintainer. It describes what the app does, how players use it, the code structure, import and categorization rules, timer math, widget and release workflow, and current limitations.

## 1. Product overview

CoC Timers is a small Android app plus Android home-screen widget for viewing active Clash of Clans upgrade timers.

The player copies the in-game village data export, then uses the app's Import action. The app reads the JSON from the clipboard, extracts upgrades that have an active timer, calculates completion estimates, saves the result locally, and refreshes the widget.

Product characteristics:

- Manual import; the app does not automatically connect to Clash of Clans.
- No account or backend is required.
- Imported state is stored on-device using SharedPreferences.
- Item names are resolved from the bundled `app/src/main/assets/names.json`.
- UI is Jetpack Compose / Material 3; the home-screen widget uses Android RemoteViews.
- The app currently targets Android API 34 and supports API 26+.

## 2. How a player uses the app

### First-time setup

1. Install and launch CoC Timers.
2. Add the **CoC Timers** widget from the Android home-screen widget picker if a widget is wanted.
3. In Clash of Clans, open **Settings → More Settings → Data Export** and copy the export JSON.
4. Tap **Import** in the app or widget. The app reads the clipboard, validates/parses the export, saves timers, and reports the result.
5. Keep the app's notification permission enabled if the app requests it. The current implementation requests notification permission, but alarm-driven upgrade-completion notifications have been removed.

### Everyday use

- Re-import after the player wants to refresh the app with current in-game timers.
- Tap a timer widget to open the app; use the widget's Import action to import from clipboard.
- The app screen shows active upgrades by category, timer remaining, and estimated local completion date/time.
- Builder Potion and Research Potion cards let the player manually model a potion used after export. A newly imported export replaces the current timer snapshot and its manual potion model.

### What importing means

Import is a snapshot replacement, not a merge of timer values. The exported timestamp is the time anchor. Timers are calculated forward from that timestamp. Since an export only includes active timers, completed/non-upgrading items are not displayed.

## 3. Build and runtime configuration

Main app module: `:app`

- Namespace/application ID: `app.coc.tracker`
- Compile SDK: 34
- Target SDK: 34
- Minimum SDK: 26
- Java/Kotlin JVM target: 17
- Compose compiler plugin enabled
- Main dependencies: Compose BOM 2024.09.00, Compose UI, Material 3, Activity Compose 1.9.2, Core KTX 1.13.1

Build from Android Studio with the normal Gradle sync/build controls, or from a terminal at the repository root:

```sh
./gradlew :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:assembleDebug
```

The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.

## 4. Startup and component flow

### Launcher flow

1. Android reads `app/src/main/AndroidManifest.xml`.
2. `MainActivity` is the exported launcher activity via MAIN/LAUNCHER intent filters.
3. `MainActivity.onCreate()` requests POST_NOTIFICATIONS on Android 13+ when needed and sets Compose content.
4. `Screen()` loads persisted State from `Store.load()`, ticks the current wall clock once per second, then renders the header, potion chips, and grouped timer cards.
5. Returning from Import causes the activity to resume and the screen reloads the stored snapshot.

### Import flow

1. UI launches the invisible, non-exported `ImportActivity`.
2. On window focus, it reads the current primary clipboard item.
3. `Parser.parse()` validates the text as a Clash of Clans export and scans arrays for positive timer values.
4. `Names.get()` maps each numeric data ID to its display name.
5. `Store.commit()` persists the new State and refreshes all active widgets.
6. ImportActivity shows a short success/failure toast and closes.

### Widget flow

- Android invokes `TimerWidget.onUpdate()` when it creates/refreshes a widget.
- `TimerWidget.refresh()` can be called after import/state commits.
- Widget UI is assembled from `RemoteViews` layouts under `app/src/main/res/layout/`.
- The widget shows one section per non-empty display category and each active timer.
- Short timers use a RemoteViews chronometer; timers of a day or more use static day/hour text until a widget refresh.
- Widget provider sizing and update period live in `app/src/main/res/xml/widget_info.xml`.

## 5. Source map

| File | Responsibility |
| --- | --- |
| `app/src/main/java/app/coc/tracker/MainActivity.kt` | Launcher Activity, Compose screen, category colors, timer cards, potion actions, 1-second display clock |
| `app/src/main/java/app/coc/tracker/ImportActivity.kt` | Clipboard import entry point; validates through Parser and commits the parsed state |
| `app/src/main/java/app/coc/tracker/Model.kt` | Group and Item models, timer finish calculations, potion windows, JSON serialization, names asset loading, ID categorization, export parser, duration/date formatting |
| `app/src/main/java/app/coc/tracker/Store.kt` | SharedPreferences persistence and widget refresh after commit |
| `app/src/main/java/app/coc/tracker/TimerWidget.kt` | AppWidgetProvider, RemoteViews construction, countdown and long-duration display |
| `app/src/main/AndroidManifest.xml` | App label/theme, launcher/import Activities, widget receiver, notification permission |
| `app/src/main/res/layout/widget*.xml` | Widget root, row and section RemoteViews layouts |
| `app/src/main/res/xml/widget_info.xml` | Widget dimensions, resize behavior and update period |
| `app/src/main/assets/names.json` | Mapping from game data IDs to readable names |
| `.github/workflows/build.yml` | GitHub Actions debug build and APK release upload |
| `README.md` | Short player-facing install/use notes |

## 6. Data model and persistence

### Group

`Group` is the category assigned to an imported active item. The visible display list is:

1. BUILDER — Home Village Builder upgrades
2. RESEARCH — Home Village Laboratory upgrades
3. PET — Pet House upgrades
4. BUILDER_HALL — Builder Base upgrades

HELPER and SUPER_OR_EVENT are classification outcomes used to exclude those IDs from upgrade rows. They are not displayed in the timer list.

### Item

An Item contains numeric game data ID, display name, current level, category, remaining seconds at export timestamp, and potion type (builder/research/neither).

### State

State contains export timestamp, active items, imported builder/research boost end timestamps, and manually modeled potion windows. State and items are serialized to JSON in a private SharedPreferences entry named `coc/state`.

**Persistence compatibility:** category values are currently serialized using enum ordinals. If the enum order changes, older saved states may be interpreted as another category. Prefer stable string names or a schema migration before changing enum ordering.

## 7. Export parser and categorization rules

The player export is treated as untrusted input: JSON parse errors return null; missing arrays are skipped; entries without a positive `timer` are ignored.

The parser scans active timers from these arrays:

- Home Village: `buildings`, `traps`, `heroes`, `guardians`, `units`, `siege_machines`, `spells`, `pets`
- Builder Base: `buildings2`, `traps2`, `heroes2`, `units2`

For ordinary Home Village entries, numeric `data` IDs are classified by `ItemCategorizer` in precedence order:

1. Explicit Builder Hall ID exceptions/ranges.
2. Explicit Super/Event IDs, classified as SUPER_OR_EVENT and skipped from active timer display.
3. Helper ID family (93 million range), classified HELPER and skipped.
4. Pet ID family (73 million range).
5. Remaining ID prefixes:
   - Prefix 1, 12, 28, 102, 103, 107 → BUILDER.
   - Prefix 4, 26 → RESEARCH.
   - Unknown IDs → SUPER_OR_EVENT fallback and skipped.

Builder Hall source arrays ending in `2` are assigned BUILDER_HALL directly, which disambiguates duplicate IDs/names used in different villages. ID is authoritative; display names are not used for classification.

The exact exception IDs are documented in `ItemCategorizer` in Model.kt. When adding a game ID rule, preserve the precedence: Builder Hall exceptions first, then Super/Event filtering, then family prefix.

## 8. Timer calculations

### Normal timers

Each imported `timer` is the amount of work remaining at export timestamp `ts`. Without potion windows:

`finish = ts + remainingSeconds`

### Potion windows

The export boost fields provide active builder/research boosts, stored as absolute end timestamps. App-entered potions are appended as manual windows. `finish()` consumes remaining work across time segments:

- Outside a boost window, one real second advances one work second.
- Inside a boost window, work advances at configured multiplier (Builder 10x, Research 24x).
- It accounts for gaps before/after windows and rounds up the final fractional second.

Pets and Builder Hall timers have potion type 0 and receive no Builder/Research potion adjustment. A new import creates a fresh State and clears the manually simulated potion windows.

### Display formatting

- App shows remaining duration using `dur()` and estimated finish time using `clock()`.
- Widget uses a live chronometer under 24 hours and day/hour text for durations at least 24 hours.

## 9. Notifications and alarms

Alarm-based upgrade completion scheduling and the alarm/boot receivers were removed. The manifest currently declares POST_NOTIFICATIONS and MainActivity requests it on Android 13+, but there is no alarm scheduler or completion notification producer in the current app flow. Do not describe notification permission as enabling finish alerts unless a notification scheduling implementation is added.

## 10. GitHub Actions and APK publishing

Workflow: `.github/workflows/build.yml`

- Runs on pushes to `main` and manual workflow dispatch.
- Uses Ubuntu, Java 17 and Gradle 8.9.
- Runs `gradle assembleDebug --no-daemon`.
- Copies the generated debug APK to `COC-Timers.apk`.
- Publishes/replaces the GitHub Release tagged `latest`, titled **Latest APK**.
- Release upload needs repository contents write permission, which the workflow declares.

The rename applies to the GitHub Release artifact; Android Studio's local debug output remains `app-debug.apk`.

The current working pattern is to branch from main, work and commit on `Dev`, push Dev, then open a pull request from Dev into main when ready. The APK workflow only runs on main pushes or manual dispatch, so pushing Dev alone does not publish a new release APK.

## 11. Release/install notes

The workflow builds a **debug-signed** APK for convenient sharing/testing. Android may require enabling “Install unknown apps” for the browser/file manager. For a production Play Store release, configure a release signing key and a release build type; do not distribute a debug APK as a store release.

## 12. Known limitations and maintenance cautions

- No live game API, account login, cloud sync, or background polling.
- Timers are only as current as the last clipboard export.
- No completion notifications are currently scheduled.
- Home-screen widget updates are host-driven plus explicit refreshes after commits; chronometers count down, but static day/hour labels do not continuously tick.
- Unknown/super/event items are excluded rather than guessed into a permanent category.
- Names asset can lag new game content; unknown names appear as `#<id>`.
- State category ordinal serialization is fragile across enum reordering.
- The parser uses one-based game levels as exported; UI text says “To level <lvl + 1>” because the imported lvl is treated as current level.
- Potion model assumes a potion activated after export starts immediately or queues after current windows. It cannot infer potion use that happened after the export unless the player imports again.

## 13. Suggested maintenance workflow

1. Start from the current main and work on Dev.
2. Inspect the sample export shape before changing parser behavior.
3. Add/adjust ID rules using IDs, never localized/display names.
4. Preserve timer math separately from parsing and UI.
5. Keep widget changes compatible with RemoteViews (supported widgets/actions only).
6. Run `:app:compileDebugKotlin` or `:app:assembleDebug` after code changes.
7. Verify a representative imported export in app and widget.
8. Open a PR from Dev to main when the work is ready; release workflow runs after main changes.

## 14. Glossary

- **Data export:** JSON copied from the Clash of Clans client.
- **data ID:** Stable numeric game content ID such as `1000008`; used for categorization.
- **timer:** Remaining upgrade work in seconds at the export timestamp.
- **ts:** Export timestamp, Unix epoch seconds.
- **boost window:** Interval where potion multiplier changes work progress.
- **RemoteViews:** Android's restricted UI system for home-screen widgets.
- **Debug APK:** Build variant signed for development/testing rather than a configured production release.
