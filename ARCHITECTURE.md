# FarmPulse architecture

FarmPulse is a sideloaded helper that sits next to official Travian Legends. It never opens a socket to Travian and it never sends troops unless the user taps Send after the interval elapses.

## Why a specialUse foreground service

The farm session is a user-visible, exact-time loop: a silent chronometer notification, an overlay countdown, and an `AlarmManager.setExactAndAllowWhileIdle` wake. That is not media playback, data sync, or phone-call work, so the session host is a `specialUse` FGS. The subtype property in the manifest states this in one sentence: timer state, silent notification, overlay keep-alive — no auto-click, no network.

WorkManager is the wrong primitive for a raid interval the user is staring at. The FGS ticks the UI once a second. The alarm is the authoritative “interval elapsed” signal, including in doze.

## Control flow

1. `MainActivity` (Compose) walks onboarding, permissions, interval, bind, and start/stop. Start is refused unless Accessibility is on, a binding exists, and `KeyguardManager.isDeviceLocked()` is false.
2. `SessionController` is the only mutable session state. `FarmSessionService` observes it, posts the ongoing notification (channel sound = None), shows the overlay while unlocked, and (re)schedules the exact alarm.
3. `RaidAlarmReceiver` calls `onRaidDue`. That path vibrates with `VibrationEffect` and flips the UI to a big Send. It must not, and does not, click Travian.
4. Send — overlay, dashboard, or the optional `ShowWhenLockedActivity` — calls `onUserTappedSend`. If the device is locked, it fails closed. Otherwise `FarmPulseAccessibilityService.performBoundClick()` runs.

`ShowWhenLockedActivity` is wake visibility only. It turns the screen on and can draw over a swipe lock. It never dismisses a secure keyguard and still refuses to click while `isDeviceLocked()` is true.

## Binding and click

Travian Legends is typically a game canvas: it does not emit `TYPE_VIEW_CLICKED`. Bind mode therefore shows a movable crosshair overlay (`TYPE_ACCESSIBILITY_OVERLAY`, touches outside pass through). The user places the target on Send and taps **Confirm bind**. That stores `com.traviangames.travianlegendsmobile` plus relative X/Y (and the pixel center). viewId / text / path stay empty.

If a real Travian click event ever arrives, the node snapshot is still stored (view id, label, parent-path fingerprint, relative center).

Click order after the user taps FarmPulse Send (never on the timer, never on Confirm bind):

1. `findAccessibilityNodeInfosByViewId` (skipped for coordinate-only binds)
2. text / contentDescription
3. walk the parent-path fingerprint
4. `ACTION_CLICK` on the node or a clickable ancestor
5. `dispatchGesture` at the saved relative coordinates

Anything else is **Rebind required**. The service is configured with `canRetrieveWindowContent`, `canPerformGestures`, and `flagRetrieveInteractiveWindows`. Package visibility uses `<queries>` for the Travian package only — no `QUERY_ALL_PACKAGES`.

## Overlay

While the session is running and the device is logically unlocked, a compact countdown chip is attached as `TYPE_ACCESSIBILITY_OVERLAY` (preferred, hosted by the a11y service) or `TYPE_APPLICATION_OVERLAY` (if Appear on top is granted). At T=0 the chip grows a Send button. If the keyguard locks, the overlay is torn down so we never present a click path through a secure lock.

## What this build will not do

Play Store distribution, Chrome Travian, multi-farmlist, root, Travian network clients, timer auto-click, or secure-keyguard click-through.
