package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.supabaseAuthStore: DataStore<Preferences> by preferencesDataStore(
    name = "clipcash_supabase_auth"
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val fullName: String = "",
    val username: String = "",
    val nationality: String = "",
    val phoneNumber: String = ""
)

class SupabaseSessionStore(private val context: Context) {
    private val keyAccessToken = stringPreferencesKey("access_token")
    private val keyRefreshToken = stringPreferencesKey("refresh_token")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyEmail = stringPreferencesKey("email")
    private val keyFullName = stringPreferencesKey("full_name")
    private val keyUsername = stringPreferencesKey("username")
    private val keyNationality = stringPreferencesKey("nationality")
    private val keyPhoneNumber = stringPreferencesKey("phone_number")

    val sessionFlow: Flow<SupabaseSession?> = context.supabaseAuthStore.data.map { prefs ->
        val token = prefs[keyAccessToken]
        val uid = prefs[keyUserId]
        if (token.isNullOrBlank() || uid.isNullOrBlank()) {
            null
        } else {
            SupabaseSession(
                accessToken = token,
                refreshToken = prefs[keyRefreshToken].orEmpty(),
                userId = uid,
                email = prefs[keyEmail].orEmpty(),
                fullName = prefs[keyFullName].orEmpty(),
                username = prefs[keyUsername].orEmpty(),
                nationality = prefs[keyNationality].orEmpty(),
                phoneNumber = prefs[keyPhoneNumber].orEmpty()
            )
        }
    }

    suspend fun getSession(): SupabaseSession? = sessionFlow.first()

    suspend fun saveSession(session: SupabaseSession) {
        context.supabaseAuthStore.edit { prefs ->
            prefs[keyAccessToken] = session.accessToken
            prefs[keyRefreshToken] = session.refreshToken
            prefs[keyUserId] = session.userId
            prefs[keyEmail] = session.email
            prefs[keyFullName] = session.fullName
            prefs[keyUsername] = session.username
            prefs[keyNationality] = session.nationality
            prefs[keyPhoneNumber] = session.phoneNumber
        }
    }

    suspend fun clearSession() {
        context.supabaseAuthStore.edit { it.clear() }
    }
}
