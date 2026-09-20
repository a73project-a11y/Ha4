# FarmPulse

Sideloaded Android MVP for Pavel’s **Samsung Galaxy S25 Ultra** (One UI 7 / Android 15).

FarmPulse is a **native Travian Legends** farm-list helper. It runs an interval timer, vibrates when the interval elapses, and waits for **you** to tap Send. Only then does its AccessibilityService click the one farmlist Send button you bound.

It does **not** talk to Travian servers. It does **not** auto-send. It does **not** click through a secure lock screen.

| | |
| --- | --- |
| Application id | `app.farmpulse` |
| Travian package | `com.traviangames.travianlegendsmobile` |
| Debug APK | [`artifacts/farmpulse-debug.apk`](artifacts/farmpulse-debug.apk) |
| Stack | Kotlin, Jetpack Compose, AccessibilityService, AlarmManager, specialUse FGS |
| minSdk / target | 26 / 35 |

## Sideload on Galaxy S25 Ultra (One UI 7)

1. Copy `artifacts/farmpulse-debug.apk` to the phone (USB, Drive, or Messages).
2. **Auto Blocker** (Security and privacy) blocks unknown installs. Turn Auto Blocker **off** for this setup, or you will not be able to install or enable Accessibility.
3. Open the APK with Files / My Files. Allow **Install unknown apps** for that installer.
4. After install, open **Settings → Apps → FarmPulse → ⋮ → Allow restricted settings**. Sideloaded apps cannot enable Accessibility until this is granted (Android 13+ / One UI).
5. Do **not** look for FarmPulse on the Play Store. This build is debug-signed for sideload only.

### Samsung blockers that stop the helper

| Blocker | What happens | Fix |
| --- | --- | --- |
| Auto Blocker | Install blocked, or Accessibility greyed out | Security and privacy → Auto Blocker → Off |
| Restricted settings | Accessibility toggle missing / rejected | App info → ⋮ → Allow restricted settings |
| Sleeping / Deep sleeping apps | Timer dies in the background | Battery → Unrestricted, add to **Never sleeping apps** |
| Adaptive battery / unused-app sleep | Same | Keep FarmPulse in Never sleeping apps |
| Exact alarms denied | Interval fires late | Alarms and reminders → FarmPulse → Allow |
| Appear on top denied | Overlay missing (a11y overlay is the fallback) | Appear on top → FarmPulse → Allow |
| Notifications denied | Silent chronometer not shown | Notifications → Allow |

FarmPulse’s onboarding deep-links as many of these screens as One UI exposes.

## Permissions (onboarding checklist)

Turn these on before a farm session. **Start Session is blocked** until Accessibility is on, a binding is saved, and the device is logically unlocked.

1. **Accessibility** — Settings → Accessibility → Installed apps → FarmPulse Travian helper.
2. **Appear on top** — overlay countdown / Send chip.
3. **Notifications** — silent ongoing chronometer (channel sound = None).
4. **Exact alarms** — `AlarmManager.setExactAndAllowWhileIdle` for the raid interval.
5. **Battery unrestricted** + **Never sleeping apps**.
6. **Allow restricted settings** (sideload) and **Auto Blocker** as above.

## Unlock rule

Farm sessions require `KeyguardManager.isDeviceLocked() == false`.

- Screen **may be off**.
- Smart Lock, Extend Unlock, and swipe-to-unlock are OK.
- A secure PIN / pattern / password keyguard that is still showing is **not** OK.
- FarmPulse will **not** implement secure-keyguard click-through. If the phone is locked, Start Session is blocked and Send will not click Travian.

## Bind the farmlist Send button

1. Install official **Travian Legends** (`com.traviangames.travianlegendsmobile`). Chrome Travian is not supported.
2. Enable the FarmPulse accessibility service.
3. In Travian, open the farm list you want.
4. In FarmPulse tap **Bind Send**, then tap that **Send** control in Travian.
5. FarmPulse stores package, `viewIdResourceName`, text / contentDescription, bounds center + relative %, and a parent-path fingerprint.

Only **one** binding is saved. Multi-farmlist is a non-goal.

## Run a session

1. Unlock the phone. Pick an interval (default **10 minutes**).
2. Tap **Start session**. A silent foreground notification starts counting down. An overlay chip appears while unlocked.
3. At 0:00 the phone **vibrates only** (no sound). A big **Send** control appears.
4. You tap **Send**. The accessibility service clicks the bound Travian button (viewId → text → parent path → `ACTION_CLICK`, then a coordinate gesture). If none of that works: **Rebind required**.
5. The next interval arms only after a successful user-confirmed Send.

The timer **never** clicks Travian by itself.

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk-17-or-21
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# committed copy: artifacts/farmpulse-debug.apk
```

## Limits (MVP)

- One bound control, native Travian only.
- No Play Store listing, no Origin, no root, no Travian HTTP/API.
- No auto-send, no secure-keyguard click-through.
- Session does not survive reboot.
- Overlay / click quality depends on Travian exposing real accessibility nodes. A single game canvas may need the coordinate fallback — rebind if the layout moves.
- One UI may still kill background work if battery is not Unrestricted.

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit.
