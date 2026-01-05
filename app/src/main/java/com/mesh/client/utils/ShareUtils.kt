package com.mesh.client.utils

import android.content.Context
import android.content.Intent

object ShareUtils {
    fun shareInvite(context: Context, meshId: String?, nickname: String?) {
        if (meshId.isNullOrBlank()) return

        // Use production domain instead of IP address
        val baseUrl = "https://mesh-online.org" 
        val encodedNickname = if (nickname != null) java.net.URLEncoder.encode(nickname, "UTF-8") else ""
        val link = "$baseUrl/invite/$meshId?n=$encodedNickname"
        
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Let's chat on Mesh! $link")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Invite Friend via")
        // Verify that the intent resolves to an activity
        if (sendIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(shareIntent)
        } else {
            // Fallback: try starting the chooser directly, modern Android might handle it better or it is safe enough
             context.startActivity(shareIntent)
        }
    }
}
