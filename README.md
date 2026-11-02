# CoC Timers

Minimal Android widget and app for Clash of Clans upgrade timers. Manual import, no network, no account.

## Get the APK (no Android Studio needed)
1. Create a GitHub repo and push this folder to the `main` branch.
2. Open the repo's **Actions** tab. The "Build APK" run takes about 4 minutes.
3. On your phone, open the repo's **Releases** page, download `app-debug.apk` from "Latest APK", install it (allow "install unknown apps" once).
4. Share that Releases link with anyone who wants it.

## Use
1. Long-press the home screen, Widgets, CoC Timers. Add it.
2. In game: Settings, More Settings, Data Export. This copies the JSON.
3. Tap **Import** on the widget. It reads the clipboard in a blink and closes. No text box anywhere.
4. Open the app once so it can ask for notification permission.

## Potions
A Builder Potion boosts all builders 10x for 1 hour (home village). A Research Potion boosts the Lab 24x for 1 hour.
- A boost already running when you exported is read from the export (`boosts.builder_boost`) and applied automatically.
- A potion you drink after exporting: tap the potion card in the app, "Use now". Every affected timer and alarm adjusts. Drinking while one is running queues after it.
- Importing a fresh export replaces everything, so the game's own numbers always win.

## Notes
- Pets are not touched by the Research Potion (the game has a separate Pet Potion). Builder Base timers show in the app only.
- Item names come from ClashKingInc/ClashKingAssets `static_data.json`. If a new item shows as `#123456`, regenerate `app/src/main/assets/names.json`.
- Set battery usage for the app to "Unrestricted" in One UI so alarms are never delayed.
