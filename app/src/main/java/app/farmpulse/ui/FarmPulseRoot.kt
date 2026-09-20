package app.farmpulse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmpulse.Travian
import app.farmpulse.data.AppPreferences
import app.farmpulse.session.BindPhase
import app.farmpulse.session.SessionController
import app.farmpulse.session.SessionPhase
import app.farmpulse.session.SessionUiState
import app.farmpulse.util.AppPermissions
import app.farmpulse.util.PermissionSnapshot
import app.farmpulse.util.SamsungIntents
import java.text.DateFormat
import java.util.Date

@Composable
fun FarmPulseRoot(
    session: SessionUiState,
    permissions: PermissionSnapshot,
    onRefreshPermissions: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    var onboardingPage by remember {
        mutableIntStateOf(if (AppPreferences.onboardingDone) -1 else 0)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Soil) {
        if (onboardingPage >= 0) {
            Onboarding(
                page = onboardingPage,
                permissions = permissions,
                onNext = {
                    if (onboardingPage >= LAST_ONBOARDING) {
                        AppPreferences.onboardingDone = true
                        onboardingPage = -1
                    } else {
                        onboardingPage += 1
                    }
                },
                onBack = { if (onboardingPage > 0) onboardingPage -= 1 },
                onRefreshPermissions = onRefreshPermissions,
                onRequestNotifications = onRequestNotifications,
            )
        } else {
            Dashboard(
                session = session,
                permissions = permissions,
                onShowOnboarding = { onboardingPage = 0 },
                onRefreshPermissions = onRefreshPermissions,
                onRequestNotifications = onRequestNotifications,
            )
        }
    }
}

private const val LAST_ONBOARDING = 5

@Composable
private fun Onboarding(
    page: Int,
    permissions: PermissionSnapshot,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("FarmPulse", color = Wheat, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Setup ${page + 1} / ${LAST_ONBOARDING + 1}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                0 -> {
                    Heading("A farm-list pulse for Travian Legends")
                    Body("Native Travian only — package ${Travian.PACKAGE_NAME}. Chrome Travian is not supported.")
                    Body("FarmPulse never talks to Travian servers. It never sends troops on its own. The interval timer only vibrates. You tap Send. Then — and only then — the accessibility service clicks the bound farmlist button.")
                }
                1 -> {
                    Heading("How a session works")
                    Body("1. Bind the farmlist Send button once.")
                    Body("2. Start a session while the phone is logically unlocked.")
                    Body("3. Overlay counts down. At 0:00 the phone vibrates — no sound.")
                    Body("4. A big Send control appears. You tap it.")
                    Body("5. FarmPulse clicks the bound Travian button. The next interval starts.")
                    Body("If the overlay cannot find that button, it fails closed: Rebind required.")
                }
                2 -> {
                    Heading("Unlock rule (locked on purpose)")
                    Body("A farm session requires KeyguardManager.isDeviceLocked() == false.")
                    Body("The screen may be off. Smart Lock, Extend Unlock, and swipe-to-unlock are OK.")
                    Body("FarmPulse will not click through a secure PIN / pattern / password keyguard. Start Session is blocked while the device is locked. If you lock the phone mid-session, overlay Send and Travian clicks stay blocked until you unlock.")
                    StatusLine("Device locked now", permissions.deviceLocked, invert = true)
                }
                3 -> {
                    Heading("Permissions")
                    Body("Sideloaded apps on Samsung One UI need an extra step before Accessibility can be turned on: App info → ⋮ → Allow restricted settings. Auto Blocker can hide that switch.")
                    PermRow("Accessibility", permissions.accessibilityEnabled) {
                        context.startActivity(AppPermissions.accessibilitySettings())
                    }
                    PermRow("Appear on top", permissions.overlayEnabled) {
                        context.startActivity(AppPermissions.overlaySettings(context))
                    }
                    PermRow("Notifications", permissions.notificationsEnabled) {
                        onRequestNotifications()
                        context.startActivity(AppPermissions.notificationSettings(context))
                    }
                    PermRow("Exact alarms", permissions.exactAlarmEnabled) {
                        context.startActivity(AppPermissions.exactAlarmSettings(context))
                    }
                    TextButton(onClick = onRefreshPermissions) { Text("Re-check permissions") }
                }
                4 -> {
                    Heading("Samsung One UI")
                    Body("Samsung One UI may sleep a sideloaded helper unless battery is Unrestricted and the app is in Never sleeping apps.")
                    Body("Auto Blocker (Security and privacy) blocks unknown-app installs and restricted settings. Turn it off for this setup, or install then use Allow restricted settings.")
                    Button(onClick = { context.startActivity(AppPermissions.batteryOptimizationSettings(context)) }) {
                        Text("Battery unrestricted")
                    }
                    Button(onClick = { context.startActivity(SamsungIntents.neverSleepingApps(context)) }) {
                        Text("Never sleeping apps")
                    }
                    Button(onClick = { context.startActivity(SamsungIntents.autoBlocker(context)) }) {
                        Text("Auto Blocker / security")
                    }
                    StatusLine("Battery unrestricted", permissions.batteryUnrestricted)
                }
                else -> {
                    Heading("Bind one farmlist Send")
                    Body("Open Travian Legends, go to the farm list you want, tap Bind, then tap that Send button. FarmPulse stores the view id, label, parent path, and relative tap point. Only this one control is used.")
                    StatusLine("Travian Legends installed", permissions.travianInstalled)
                    StatusLine("Accessibility on", permissions.accessibilityEnabled)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (page > 0) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Wheat, contentColor = Soil),
            ) {
                Text(if (page == LAST_ONBOARDING) "Open dashboard" else "Next")
            }
        }
    }
}

@Composable
private fun Dashboard(
    session: SessionUiState,
    permissions: PermissionSnapshot,
    onShowOnboarding: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val canStart = permissions.accessibilityEnabled &&
        session.binding != null &&
        !permissions.deviceLocked &&
        !session.running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FarmPulse", color = Wheat, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Travian Legends farm helper", color = Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onShowOnboarding) { Text("Setup") }
        }

        CardBlock {
            val headline = when {
                !session.running -> "Idle"
                session.phase == SessionPhase.AWAITING_SEND -> "Tap Send"
                else -> session.remainingLabel
            }
            Text(headline, fontSize = 48.sp, fontWeight = FontWeight.Light, color = Cream)
            Text(
                text = when {
                    session.running && session.phase == SessionPhase.AWAITING_SEND ->
                        "Vibrated. The timer did not and will not click Travian."
                    session.running ->
                        "Counting down ${session.intervalMinutes} min. Overlay shows this while unlocked."
                    permissions.deviceLocked ->
                        "Phone is locked. Unlock before starting a session."
                    else -> "Start a session after Accessibility + binding + unlock."
                },
                color = Muted,
            )
            if (session.running && session.phase == SessionPhase.AWAITING_SEND) {
                Button(
                    onClick = { SessionController.onUserTappedSend(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = PulseGreen, contentColor = Soil),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Text("SEND", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.running) {
                    OutlinedButton(
                        onClick = { SessionController.stopSession(context) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop session") }
                } else {
                    Button(
                        onClick = { SessionController.startSession(context) },
                        enabled = canStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Wheat, contentColor = Soil),
                    ) { Text("Start session") }
                }
            }
            if (!canStart && !session.running) {
                val why = buildList {
                    if (!permissions.accessibilityEnabled) add("Accessibility is off")
                    if (session.binding == null) add("No farmlist button bound")
                    if (permissions.deviceLocked) add("Device is locked")
                }.joinToString(" · ")
                if (why.isNotBlank()) Text(why, color = Danger, fontSize = 13.sp)
            }
        }

        CardBlock {
            Text("Interval", fontWeight = FontWeight.SemiBold, color = Wheat)
            Text("Default 10 minutes. AlarmManager exact alarm — not WorkManager.", color = Muted, fontSize = 13.sp)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPreferences.INTERVAL_CHOICES.forEach { minutes ->
                    FilterChip(
                        selected = session.intervalMinutes == minutes,
                        onClick = { SessionController.setIntervalMinutes(minutes) },
                        label = { Text("${minutes}m") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Wheat,
                            selectedLabelColor = Soil,
                        ),
                    )
                }
            }
        }

        CardBlock {
            Text("Farmlist Send binding", fontWeight = FontWeight.SemiBold, color = Wheat)
            if (session.binding == null) {
                Text("No button saved. Bind the Send control in native Travian Legends.", color = Muted)
            } else {
                val b = session.binding
                Text("Package: ${b.packageName}", fontSize = 13.sp)
                Text("viewId: ${b.viewIdResourceName ?: "—"}", fontSize = 13.sp)
                Text("label: ${b.text ?: b.contentDescription ?: "—"}", fontSize = 13.sp)
                val whenBound = DateFormat.getDateTimeInstance().format(Date(b.boundAtMillis))
                Text("saved $whenBound", color = Muted, fontSize = 12.sp)
            }
            val binding = session.bindPhase == BindPhase.WAITING_FOR_TRAVIAN_CLICK
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { SessionController.beginBind(context) },
                    enabled = permissions.accessibilityEnabled && !binding,
                    modifier = Modifier.weight(1f),
                ) { Text(if (binding) "Waiting for tap…" else "Bind Send") }
                OutlinedButton(
                    onClick = { SessionController.cancelBind() },
                    enabled = binding,
                ) { Text("Cancel") }
            }
            if (session.binding != null) {
                TextButton(onClick = { SessionController.clearBinding() }) { Text("Clear binding") }
            }
            if (!permissions.travianInstalled) {
                Text("Travian Legends is not installed (${Travian.PACKAGE_NAME}).", color = Danger, fontSize = 13.sp)
            } else {
                TextButton(onClick = { AppPermissions.launchTravian(context) }) { Text("Open Travian Legends") }
            }
        }

        CardBlock {
            Text("Permissions & Samsung", fontWeight = FontWeight.SemiBold, color = Wheat)
            PermRow("Accessibility", permissions.accessibilityEnabled) {
                context.startActivity(AppPermissions.accessibilitySettings())
            }
            PermRow("Appear on top", permissions.overlayEnabled) {
                context.startActivity(AppPermissions.overlaySettings(context))
            }
            PermRow("Notifications", permissions.notificationsEnabled) {
                onRequestNotifications()
                context.startActivity(AppPermissions.notificationSettings(context))
            }
            PermRow("Exact alarms", permissions.exactAlarmEnabled) {
                context.startActivity(AppPermissions.exactAlarmSettings(context))
            }
            PermRow("Battery unrestricted", permissions.batteryUnrestricted) {
                context.startActivity(AppPermissions.batteryOptimizationSettings(context))
            }
            PermRow("Device unlocked", !permissions.deviceLocked)
            TextButton(onClick = { context.startActivity(SamsungIntents.neverSleepingApps(context)) }) {
                Text("Never sleeping apps")
            }
            TextButton(onClick = { context.startActivity(SamsungIntents.autoBlocker(context)) }) {
                Text("Auto Blocker / restricted settings")
            }
            TextButton(onClick = onRefreshPermissions) { Text("Re-check") }
        }

        if (session.lastStatus.isNotBlank()) {
            CardBlock {
                Text("Status", fontWeight = FontWeight.SemiBold, color = Wheat)
                Text(session.lastStatus, fontSize = 14.sp)
            }
        }

        Text(
            "sideload debug · no Play Store · no auto-send · no Travian API",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun CardBlock(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoilCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Cream)
}

@Composable
private fun Body(text: String) {
    Text(text, color = Cream.copy(alpha = 0.9f), fontSize = 16.sp, lineHeight = 22.sp)
}

@Composable
private fun StatusLine(label: String, ok: Boolean, invert: Boolean = false) {
    val good = if (invert) !ok else ok
    Text(
        text = "${if (good) "OK" else "NEED"}  $label",
        color = if (good) PulseGreen else Danger,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            StatusLine(label, ok)
        }
        if (!ok && onFix != null) {
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}
