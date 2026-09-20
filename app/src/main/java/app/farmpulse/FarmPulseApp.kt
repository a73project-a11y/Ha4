package app.farmpulse

import android.app.Application
import app.farmpulse.data.AppPreferences
import app.farmpulse.session.SessionController

class FarmPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
        SessionController.attach(this)
    }
}
