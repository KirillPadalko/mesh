package com.mesh.client.updates

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * UpdateManager handles checking for updates and installing them automatically.
 * 
 * Usage: Call checkForUpdates() from MainActivity.onCreate()
 */
class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_SERVER_URL = "http://34.78.2.164:8001" // TODO: Replace with actual server URL
        private const val CURRENT_VERSION = "0.1.0" // TODO: Sync with build.gradle versionName
    }
    
    data class UpdateResponse(
        @SerializedName("has_update") val hasUpdate: Boolean,
        @SerializedName("latest_version") val latestVersion: String?,
        @SerializedName("url") val url: String?,
        @SerializedName("mandatory") val mandatory: Boolean?,
        @SerializedName("release_notes") val releaseNotes: String?
    )
    
    /**
     * Checks for updates and installs them if available.
     * This is a non-blocking call that runs on IO dispatcher.
     */
    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking for updates...")
                
                val updateInfo = fetchUpdateInfo()
                
                if (updateInfo.hasUpdate && updateInfo.url != null) {
                    Log.i(TAG, "Update available: ${updateInfo.latestVersion}")
                    
                    val apkFile = downloadApk(updateInfo.url)
                    
                    if (apkFile.exists()) {
                        Log.i(TAG, "APK downloaded successfully, initiating install...")
                        installApk(apkFile)
                    } else {
                        Log.e(TAG, "Downloaded APK file does not exist")
                    }
                } else {
                    Log.d(TAG, "No updates available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
            }
        }
    }
    
    private fun fetchUpdateInfo(): UpdateResponse {
        val requestUrl = "$UPDATE_SERVER_URL/api/v1/system/update?version=$CURRENT_VERSION&platform=android"
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Update check failed: ${response.code}")
            }
            
            val body = response.body?.string() ?: throw Exception("Empty response body")
            return gson.fromJson(body, UpdateResponse::class.java)
        }
    }
    
    private fun downloadApk(url: String): File {
        Log.d(TAG, "Downloading APK from: $url")
        
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        // Create updates directory in cache
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        
        val apkFile = File(updatesDir, "update.apk")
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("APK download failed: ${response.code}")
            }
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Empty APK response body")
        }
        
        Log.d(TAG, "APK downloaded to: ${apkFile.absolutePath}")
        return apkFile
    }
    
    private fun installApk(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "com.mesh.client.fileprovider",
            apkFile
        )
        
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        context.startActivity(installIntent)
        Log.i(TAG, "Install intent launched")
    }
    
    /**
     * Compares two semantic version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            
            when {
                p1 > p2 -> return 1
                p1 < p2 -> return -1
            }
        }
        
        return 0
    }
}
