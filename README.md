<p align="center">
  <img src="docs/icon.png" width="96" alt="DevMode Helper icon">
</p>

<h1 align="center">DevMode Helper</h1>

<p align="center">
  One-tap Android development mode: USB debugging, wireless debugging and "stay awake" turn on together,
  and all of them turn off again when you are done. Works automatically with Samsung <b>Modes and Routines</b>.
</p>

---

Testing apps on a phone (for example letting an AI coding agent drive tests over adb) gets tedious when the
screen keeps turning off and locking, and when USB debugging, wireless debugging and Samsung **Auto Blocker**
have to be toggled by hand every time. For security, those settings should also stay off during normal use.
DevMode Helper combines both: everything on while you develop, everything off when you finish.

> The app's user interface is currently in Turkish.

<p align="center">
  <img src="docs/screenshot-main.png" width="300" alt="Main screen">
</p>

## Features

- **One main switch**: USB debugging, wireless debugging and "stay awake while charging" turn on and off
  together. Each one can also be toggled on its own.
- **Samsung mode sync**: when the mode you created in Modes and Routines (default name `Development`,
  changeable in the app) turns on, the settings turn on; when the mode ends, they all turn off.
- **Live state**: every value on screen is read from the phone's own settings at that moment; the app does
  not keep its own "is it on" state. If you change a setting in the system, the screen follows.
- **Auto Blocker awareness**: while Auto Blocker is on, Samsung forces debugging off. The app detects this,
  locks the affected switches and opens Samsung's Auto Blocker screen. As soon as you turn Auto Blocker off,
  the pending settings are applied. When the mode ends, a notification reminds you to turn Auto Blocker back on.
- **Persistent status notification**: while any developer setting is on, a non-dismissible notification
  stays visible; tapping it opens the app and its "Kapat" (turn off) button switches everything off.
- **Wireless debugging page**: IP address and port (from the phone's own adb mDNS announcement), a copyable
  `adb connect` command, and a shortcut to the system screen for pairing.
- **"Test apps" widget**: lists the apps you installed over adb (USB/Wi‑Fi) with their icons; tap one to
  open it. Android does not let an app place icons on a specific home screen page, so you put the widget on
  the page you want (for example page 3) once, and newly installed apps show up there.
- One UI style settings screens (switch sizes and colors taken from Samsung's own resources), light/dark
  theme and themed icon support.

## Requirements

- Android 14 or newer (minSdk 34). Tested on Samsung One UI 8.5 / Android 16.
- On non-Samsung devices the main switch and the individual switches work; mode sync and Auto Blocker
  features are Samsung-specific.
- A computer with [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)
  (`adb`), needed once for setup.

## Installation

1. Get the APK:
   - download `devmode-helper-<version>.apk` from [Releases](https://github.com/ozanmora/devmode-helper/releases)
     (check it against the `.sha256` file next to it), **or**
   - build it yourself (see below).
2. Turn on USB debugging once and install:
   ```bash
   adb install devmode-helper-<version>.apk
   ```
3. Grant the settings permission (it can only be granted over adb):
   ```bash
   adb shell pm grant works.mora.devmode android.permission.WRITE_SECURE_SETTINGS
   ```
4. Open the app once and allow notifications.
5. Settings › Apps › DevMode Helper › Battery › choose **Unrestricted**, so background triggers are not delayed.
6. (Samsung) Create a mode in Modes and Routines and give it the same name as "İzlenen Samsung modu"
   (watched Samsung mode) in the app. You can add settings that Samsung supports natively, such as screen
   timeout, to the mode itself.

If you later install a build signed with a different key (for example your own build over a release APK),
uninstall the previous version first.

## Test apps widget

1. In the app, tap **Ana ekran › Test uygulamaları widget'ı** (Home screen › Test apps widget) and add the
   widget (or long-press the home screen › Widgets › DevMode Helper).
2. Move the widget to the page you want.
3. Apps installed over adb are listed automatically. The list refreshes with the refresh button in its header,
   every 15 minutes, when DevMode Helper is opened, or from your computer with:
   ```bash
   adb shell am broadcast -n works.mora.devmode/.DevAppsWidget -a works.mora.devmode.REFRESH_DEV_APPS
   ```

> **`adb: more than one device/emulator`:** if the phone is connected over both USB and wireless debugging,
> adb sees two separate devices. Tell every adb command in this README which one to use:
> `adb -d …` sends to the single USB device, `adb -s <serial> …` sends to a specific device (the serial is
> listed by `adb devices`). If you always work with the same phone, `export ANDROID_SERIAL=<serial>` sets
> the default device.

An adb install is recognized by having no installer package
(`InstallSourceInfo.getInstallingPackageName() == null`); system apps are excluded. Since Android 8 the
"package added" broadcast is no longer delivered to apps in the background, so a new install appears through
the refreshes above rather than instantly.

## How it works

- Samsung writes the active mode to the `mode_enabled` and `mode_display_name` keys under `Settings.Global`.
  The app wakes up through a `JobScheduler` content trigger when these keys change; it does not run in the
  background all the time.
- Switching is done by writing `Settings.Global.ADB_ENABLED`, `adb_wifi_enabled` and
  `STAY_ON_WHILE_PLUGGED_IN`. When turning off, USB debugging goes last (an attached adb session drops at
  that moment).
- **Auto Blocker's value cannot be read**: Samsung protects the key with a signature permission, and
  Android 12+ does not let regular apps read it. So the state is inferred from the phone's own signals:
  if debugging is on, Auto Blocker is definitely off; the system notification sent when the key changes flips
  the state. A change can be missed if it is toggled very quickly twice; the state corrects itself as soon as
  debugging is on again.
- Samsung does not allow other apps to open the system "Wireless debugging" detail page, so the app has its
  own detail page. Device pairing can only be done by the system, so that page links to Developer options.

## Security

- `WRITE_SECURE_SETTINGS` is a powerful permission; the app uses it only for the three settings above.
  The code is open, and building and installing it yourself is recommended. To revoke the permission:
  ```bash
  adb shell pm revoke works.mora.devmode android.permission.WRITE_SECURE_SETTINGS
  ```
- The app does **not** change Auto Blocker; it only opens Samsung's own screen.
- The `INTERNET` permission exists only to find the wireless debugging port: Android requires it for local
  mDNS (`NsdManager`), and the port found is checked on the phone's own IP address. The app does not connect
  to any external server and collects no data.
- The *GitHub Sponsors* and *Buy Me a Coffee* rows in the app only open your browser when you tap them.
- Exported components: the launcher screen, the receiver for the system's protected boot broadcasts, and the
  widget receiver (required for widget updates; its custom action only refreshes the list). Notification
  buttons and widget taps use the app's own `PendingIntent`s.

## Building

No Gradle needed; only the Android SDK (build-tools 36.0.0, platform android-36) and JDK 17+.

```bash
./build.sh
# output: build/devmode-helper.apk
```

`ANDROID_HOME`, `BUILD_TOOLS` and `KEYSTORE` can be set as environment variables. The default signing key is
`~/.android/debug.keystore`; it is created if missing.

## Support

DevMode Helper is free and open source. If it saves you time, you can support its development:

- [GitHub Sponsors](https://github.com/sponsors/ozanmora)
- [Buy Me a Coffee](https://buymeacoffee.com/ozanmora)

## License

[MIT](LICENSE)

## Trademarks

Samsung, Galaxy, One UI, Modes and Routines and Auto Blocker are trademarks of Samsung Electronics Co., Ltd.
Android is a trademark of Google LLC. They are mentioned only to describe compatibility. This project is
independent and is not affiliated with, endorsed or sponsored by Samsung or Google.
