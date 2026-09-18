package com.fox.app.data.drive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DriveAuthTokenProvider {
    suspend fun getAccessToken(): String
}

/**
 * Wraps whatever Google account the user already signed into this app with
 * (via GoogleSignIn, requesting [DRIVE_READONLY_SCOPE]) and exchanges it for
 * a short-lived OAuth access token per call. No token is cached here —
 * GoogleAuthUtil handles refresh/caching internally.
 */
class GoogleSignInDriveAuthTokenProvider(private val context: Context) : DriveAuthTokenProvider {

    override suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: throw DriveAuthException("尚未使用 Google 帳號登入，或登入時未授權 Drive 唯讀範圍 ($DRIVE_READONLY_SCOPE)")
        GoogleAuthUtil.getToken(context, account, "oauth2:$DRIVE_READONLY_SCOPE")
    }

    companion object {
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    }
}
