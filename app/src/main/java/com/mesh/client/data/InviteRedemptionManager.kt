package com.mesh.client.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Manager for handling invite redemption flow.
 * 
 * Responsibilities:
 * - Store pending invites (when user clicks invite link before creating identity)
 * - Process invite redemption (send InviteAck, add contacts bi-directionally)
 * - Track processed invites to prevent duplicates
 */
class InviteRedemptionManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("mesh_invites", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREF_PENDING_INVITE = "pending_invite"
        private const val PREF_PENDING_INVITE_TIMESTAMP = "pending_invite_timestamp"
        private const val PREF_PENDING_INVITE_NICKNAME = "pending_invite_nickname"
        private const val PREF_PROCESSED_INVITES = "processed_invites"
        private const val MAX_PENDING_TIME_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Store a pending invite for later processing.
     * Used when user receives invite link before identity exists.
     */
    fun storePendingInvite(inviterMeshId: String, nickname: String?) {
        prefs.edit().apply {
            putString(PREF_PENDING_INVITE, inviterMeshId)
            putString(PREF_PENDING_INVITE_NICKNAME, nickname)
            putLong(PREF_PENDING_INVITE_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * Get pending invite if one exists and is still valid.
     * Returns Pair(meshId, nickname?) or null if no pending invite or if it's expired.
     */
    fun getPendingInvite(): Pair<String, String?>? {
        val inviterMeshId = prefs.getString(PREF_PENDING_INVITE, null) ?: return null
        val nickname = prefs.getString(PREF_PENDING_INVITE_NICKNAME, null)
        val timestamp = prefs.getLong(PREF_PENDING_INVITE_TIMESTAMP, 0L)
        
        // Check if invite is still valid (not expired)
        if (System.currentTimeMillis() - timestamp > MAX_PENDING_TIME_MS) {
            clearPendingInvite()
            return null
        }
        
        return Pair(inviterMeshId, nickname)
    }
    
    /**
     * Clear the pending invite after processing.
     */
    fun clearPendingInvite() {
        prefs.edit().apply {
            remove(PREF_PENDING_INVITE)
            remove(PREF_PENDING_INVITE_NICKNAME)
            remove(PREF_PENDING_INVITE_TIMESTAMP)
            apply()
        }
    }
    
    /**
     * Mark an invite as processed to prevent duplicate redemption.
     */
    fun markInviteProcessed(inviterMeshId: String) {
        val processed = getProcessedInvites().toMutableSet()
        processed.add(inviterMeshId)
        
        prefs.edit().apply {
            putString(PREF_PROCESSED_INVITES, gson.toJson(processed))
            apply()
        }
    }
    
    /**
     * Check if an invite has already been processed.
     */
    fun isInviteProcessed(inviterMeshId: String): Boolean {
        return getProcessedInvites().contains(inviterMeshId)
    }
    
    private fun getProcessedInvites(): Set<String> {
        val json = prefs.getString(PREF_PROCESSED_INVITES, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }



    /**
     * Check for deferred deep link invite on the server.
     * Should be called once after identity creation.
     */
    fun checkDeferredInvite(myMeshId: String, onFound: (inviterId: String) -> Unit) {
        if (prefs.getBoolean("deferred_link_checked", false)) {
            android.util.Log.d("InviteRedemptionManager", "Deferred link check skipped: already checked.")
            return
        }

        val serverUrl = com.mesh.client.BuildConfig.SERVER_URL.replace("wss://", "https://")
        val client = okhttp3.OkHttpClient()
        
        val json = """{"new_user_id": "$myMeshId"}"""
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/install/claim")
            .post(body)
            .build()
            
        // Run in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respBody = response.body?.string()
                        if (respBody != null) {
                            val jsonResp = com.google.gson.JsonParser.parseString(respBody).asJsonObject
                            if (jsonResp.has("status") && jsonResp.get("status").asString == "claimed") {
                                val inviterId = jsonResp.get("inviter_id").asString
                                withContext(Dispatchers.Main) {
                                    onFound(inviterId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors, silent fail
                e.printStackTrace()
            } finally {
                // Mark checked prevents repeated spamming
                prefs.edit().putBoolean("deferred_link_checked", true).apply()
            }
        }
    }
}
