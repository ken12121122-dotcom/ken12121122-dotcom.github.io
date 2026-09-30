package com.fox.app.data.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

/**
 * Builds the GoogleSignInClient used to request drive.readonly access for FOX's Drive sync.
 *
 * Actually signing in requires an OAuth client registered in Google Cloud Console for the
 * merged app's applicationId (com.amin.pocketgba) + release signing SHA-1, and a
 * google-services.json placed in android-native/app/ — neither exists yet as of this
 * change. Until OWNER sets that up, GoogleSignIn.getClient(...) itself still succeeds
 * (it's just local config), but the sign-in flow will fail at Google's server with a
 * DEVELOPER_ERROR (ApiException code 10). See FOX_APP_HANDOFF.md.
 */
fun buildFoxGoogleSignInClient(context: Context): GoogleSignInClient {
    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(GoogleSignInDriveAuthTokenProvider.DRIVE_READONLY_SCOPE))
        .build()
    return GoogleSignIn.getClient(context, options)
}
