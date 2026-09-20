package app.farmpulse

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.farmpulse.session.SessionController
import app.farmpulse.ui.FarmPulseRoot
import app.farmpulse.ui.FarmPulseTheme
import app.farmpulse.util.AppPermissions
import app.farmpulse.util.PermissionSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissions = MutableStateFlow(
        PermissionSnapshot(
            accessibilityEnabled = false,
            overlayEnabled = false,
            notificationsEnabled = false,
            exactAlarmEnabled = false,
            batteryUnrestricted = false,
            travianInstalled = false,
            deviceLocked = true,
        ),
    )

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionController.attach(applicationContext)
        SessionController.refreshFromDisk()
        requestNotificationPermission()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshPermissions()
                    delay(1_000L)
                }
            }
        }

        setContent {
            FarmPulseTheme {
                val session by SessionController.state.collectAsState()
                val snapshot by permissions.collectAsState()
                FarmPulseRoot(
                    session = session,
                    permissions = snapshot,
                    onRefreshPermissions = { refreshPermissions() },
                    onRequestNotifications = { requestNotificationPermission() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        SessionController.refreshFromDisk()
    }

    private fun refreshPermissions() {
        permissions.value = AppPermissions.snapshot(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
