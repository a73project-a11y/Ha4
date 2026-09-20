package app.farmpulse

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmpulse.session.SessionController
import app.farmpulse.session.SessionPhase
import app.farmpulse.ui.FarmPulseTheme
import app.farmpulse.ui.PulseGreen
import app.farmpulse.ui.Soil
import app.farmpulse.util.DeviceLock

/**
 * Optional wake visibility. Does not dismiss a secure keyguard.
 * Travian is clicked only when [DeviceLock.isDeviceLocked] is false and
 * the user taps Send.
 */
class ShowWhenLockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        setContent {
            FarmPulseTheme {
                val session by SessionController.state.collectAsState()
                val locked = DeviceLock.isDeviceLocked(this)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Soil)
                        .padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("FarmPulse", color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (session.phase == SessionPhase.AWAITING_SEND) "0:00" else session.remainingLabel,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = if (locked) {
                            "Unlock the phone first. FarmPulse will not click Travian through a secure lock screen."
                        } else {
                            "Timer will not send. Tap Send to click the bound farmlist button."
                        },
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Button(
                        onClick = {
                            SessionController.onUserTappedSend(applicationContext)
                            if (!DeviceLock.isDeviceLocked(this@ShowWhenLockedActivity)) {
                                finish()
                            }
                        },
                        enabled = session.phase == SessionPhase.AWAITING_SEND && !locked,
                        colors = ButtonDefaults.buttonColors(containerColor = PulseGreen),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("SEND", fontSize = 22.sp, color = Soil)
                    }
                    Button(
                        onClick = { finish() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
