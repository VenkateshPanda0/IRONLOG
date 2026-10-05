package app.ironlog.personal.data.cloud

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import app.ironlog.personal.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** The signed-in Google account as shown in the app. Nothing else about the user is fetched. */
data class GoogleAccount(val email: String, val name: String, val photoUrl: String?)

class SignInCancelled : Exception("Sign-in cancelled")

/**
 * Optional Google sign-in with Credential Manager. Ironlog has no server: the account only labels
 * the profile and unlocks backup to the user's own Google Drive app folder.
 */
object GoogleSignIn {
    val configured: Boolean
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    const val NOT_CONFIGURED =
        "Google sign-in is not set up in this build. Add googleWebClientId to local.properties (see README) and rebuild."

    suspend fun signIn(activity: Activity): GoogleAccount {
        check(configured) { NOT_CONFIGURED }
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
                .build()
        val credential =
            try {
                CredentialManager.create(activity).getCredential(activity, request).credential
            } catch (_: GetCredentialCancellationException) {
                throw SignInCancelled()
            }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            return GoogleAccount(google.id, google.displayName ?: google.givenName ?: google.id.substringBefore('@'), google.profilePictureUri?.toString())
        }
        error("Unexpected credential type")
    }

    /** Forgets the chosen account in Credential Manager; bounded because it relies on Play services. */
    suspend fun signOut(context: Context) {
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(5_000) { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        }
    }

    /** Asks for Drive app-folder access; the result either has a token or needs the user's consent. */
    suspend fun authorizeDrive(activity: Activity, email: String?): AuthorizationResult {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveBackup.SCOPE)))
        if (email != null) builder.setAccount(Account(email, "com.google"))
        return Identity.getAuthorizationClient(activity).authorize(builder.build()).await()
    }

    /** Reads the token after the consent screen returned. */
    fun tokenFromConsent(activity: Activity, data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
