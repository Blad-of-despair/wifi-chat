package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "homelink_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_AVATAR_COLOR = longPreferencesKey("avatar_color")
        val KEY_AVATAR_URI = stringPreferencesKey("avatar_uri")
        val KEY_FAMILY_PIN = stringPreferencesKey("family_pin")
        val KEY_IS_HUB = booleanPreferencesKey("is_hub")
        val KEY_MANUAL_HUB_IP = stringPreferencesKey("manual_hub_ip")
        val KEY_MANUAL_HUB_PORT = stringPreferencesKey("manual_hub_port")
        val KEY_STATUS_MESSAGE = stringPreferencesKey("status_message")
        val KEY_IS_DND = booleanPreferencesKey("is_dnd")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val isOnboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_ONBOARDED] ?: false }
    val userIdFlow: Flow<String> = context.dataStore.data.map { it[KEY_USER_ID] ?: "" }
    val userNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_USER_NAME] ?: "Family Member" }
    val avatarColorFlow: Flow<Long> = context.dataStore.data.map { it[KEY_AVATAR_COLOR] ?: 0xFF0284C7 }
    val avatarUriFlow: Flow<String?> = context.dataStore.data.map { it[KEY_AVATAR_URI] }
    val familyPinFlow: Flow<String> = context.dataStore.data.map { it[KEY_FAMILY_PIN] ?: "1234" }
    val isHubFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_HUB] ?: false }
    val manualHubIpFlow: Flow<String> = context.dataStore.data.map { it[KEY_MANUAL_HUB_IP] ?: "" }
    val manualHubPortFlow: Flow<String> = context.dataStore.data.map { it[KEY_MANUAL_HUB_PORT] ?: "8888" }
    val statusMessageFlow: Flow<String> = context.dataStore.data.map { it[KEY_STATUS_MESSAGE] ?: "Home" }
    val isDndFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_IS_DND] ?: false }
    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }

    suspend fun getUserId(): String {
        val existing = context.dataStore.data.map { it[KEY_USER_ID] }.first()
        if (!existing.isNullOrEmpty()) return existing
        val newId = UUID.randomUUID().toString().take(8)
        context.dataStore.edit { it[KEY_USER_ID] = newId }
        return newId
    }

    suspend fun saveProfile(
        name: String,
        avatarColor: Long,
        avatarUri: String?,
        familyPin: String,
        isHub: Boolean
    ) {
        val uid = getUserId()
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_ONBOARDED] = true
            prefs[KEY_USER_ID] = uid
            prefs[KEY_USER_NAME] = name
            prefs[KEY_AVATAR_COLOR] = avatarColor
            if (avatarUri != null) prefs[KEY_AVATAR_URI] = avatarUri else prefs.remove(KEY_AVATAR_URI)
            prefs[KEY_FAMILY_PIN] = familyPin
            prefs[KEY_IS_HUB] = isHub
        }
    }

    suspend fun updateHubMode(isHub: Boolean) {
        context.dataStore.edit { it[KEY_IS_HUB] = isHub }
    }

    suspend fun updateManualHub(ip: String, port: String) {
        context.dataStore.edit {
            it[KEY_MANUAL_HUB_IP] = ip
            it[KEY_MANUAL_HUB_PORT] = port
        }
    }

    suspend fun updateStatus(status: String) {
        context.dataStore.edit { it[KEY_STATUS_MESSAGE] = status }
    }

    suspend fun updateDnd(isDnd: Boolean) {
        context.dataStore.edit { it[KEY_IS_DND] = isDnd }
    }

    suspend fun updateTheme(themeMode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = themeMode }
    }

    suspend fun updateFamilyPin(pin: String) {
        context.dataStore.edit { it[KEY_FAMILY_PIN] = pin }
    }
}
