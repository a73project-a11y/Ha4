package app.farmpulse.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AppPreferences {
    const val DEFAULT_INTERVAL_MINUTES = 10
    val INTERVAL_CHOICES = listOf(5, 7, 10, 12, 15, 20, 30)

    private const val PREFS = "farmpulse"
    private const val KEY_INTERVAL = "interval_minutes"
    private const val KEY_ONBOARDING = "onboarding_done"
    private const val KEY_BINDING = "binding_json"
    private const val KEY_LAST_STATUS = "last_status"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MINUTES).coerceAtLeast(1)
        set(value) {
            prefs.edit().putInt(KEY_INTERVAL, value.coerceAtLeast(1)).apply()
        }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()
        }

    var lastStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LAST_STATUS, value).apply()
        }

    var binding: ButtonBinding?
        get() {
            val raw = prefs.getString(KEY_BINDING, null) ?: return null
            return runCatching { json.decodeFromString<ButtonBinding>(raw) }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_BINDING).apply()
            } else {
                prefs.edit().putString(KEY_BINDING, json.encodeToString(value)).apply()
            }
        }

    val hasBinding: Boolean get() = binding != null
}
