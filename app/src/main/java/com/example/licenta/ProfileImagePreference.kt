package com.example.licenta.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "profile_prefs")

class ProfileImagePreference(private val context: Context) {

    companion object {
        val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
    }

    val profileImageUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PROFILE_IMAGE_URI]
    }

    suspend fun saveProfileImageUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[PROFILE_IMAGE_URI] = uri
        }
    }
}
