package com.kashyap.wizlyai.android.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.kashyap.wizlyai.auth.GoogleAuthService
import com.kashyap.wizlyai.data.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await

class AndroidGoogleAuthService(
    private val context: Context,
) : GoogleAuthService {
    private val CLIENT_ID: String by lazy {
        try {
            val applicationContext = context.applicationContext
            val resId =
                applicationContext.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    applicationContext.packageName,
                )
            if (resId != 0) {
                applicationContext.getString(resId)
            } else {
                Log.e("GoogleAuth", "default_web_client_id not found in resources")
                ""
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Error getting CLIENT_ID: ${e.message}")
            ""
        }
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val signInClient: GoogleSignInClient
    private val isSignedInFlow = MutableStateFlow(false)

    private var signInCallback: (User?) -> Unit = {}

    init {
        Log.d("GoogleSignIn", "Initializing AndroidGoogleAuthService")
        Log.d("GoogleSignIn", "CLIENT_ID: $CLIENT_ID")

        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(CLIENT_ID)
                .requestEmail()
                .build()

        signInClient = GoogleSignIn.getClient(context, gso)

        // Check if user is already signed in
        val currentUser = auth.currentUser
        isSignedInFlow.value = currentUser != null
        Log.d("GoogleSignIn", "Current sign-in state: ${isSignedInFlow.value}")

        if (currentUser != null) {
            Log.d("GoogleSignIn", "Current user: ${currentUser.email} (${currentUser.uid})")
        }
    }

    companion object {
        fun setupResultHandler(
            activity: ComponentActivity,
            authService: AndroidGoogleAuthService,
        ): ActivityResultLauncher<Intent> {
            return activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                Log.d("GoogleSignIn", "Activity result received with code: ${result.resultCode}")
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                authService.handleSignInResult(task)
            }
        }
    }

    fun startSignIn(
        launcher: ActivityResultLauncher<Intent>,
        callback: (User?) -> Unit,
    ) {
        Log.d("GoogleSignIn", "Starting sign-in process...")
        signInCallback = callback

        try {
            val signInIntent = signInClient.signInIntent
            launcher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Error launching sign-in intent: ${e.message}", e)
            signInCallback(null)
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d("GoogleSignIn", "Google sign-in successful for: ${account.email}")

            // Get ID token for Firebase authentication
            val idToken = account.idToken
            if (idToken == null) {
                Log.e("GoogleSignIn", "ID token is null")
                signInCallback(null)
                return
            }

            // Authenticate with Firebase
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    Log.d("GoogleSignIn", "Firebase authentication successful")
                    val user = createUserFromFirebaseUser(firebaseUser)
                    Log.d("GoogleSignIn", "User object created: ${user.email}")

                    isSignedInFlow.value = true
                    signInCallback(user)
                } else {
                    Log.e("GoogleSignIn", "Firebase user is null after sign in")
                    isSignedInFlow.value = false
                    signInCallback(null)
                }
            }.addOnFailureListener { e ->
                Log.e("GoogleSignIn", "Firebase signInWithCredential failed: ${e.message}", e)
                if (e is FirebaseAuthException) {
                    Log.e("GoogleSignIn", "Firebase Auth Error Code: ${e.errorCode}")
                }
                isSignedInFlow.value = false
                signInCallback(null)
            }
        } catch (e: ApiException) {
            Log.e("GoogleSignIn", "Google sign-in failed: ${e.message}")
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> {
                    Log.d("GoogleSignIn", "User cancelled sign-in")
                }
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> {
                    Log.e("GoogleSignIn", "Sign-in failed")
                }
                GoogleSignInStatusCodes.NETWORK_ERROR -> {
                    Log.e("GoogleSignIn", "Network error during sign-in")
                }
                else -> {
                    Log.e("GoogleSignIn", "Unknown error: ${e.statusCode}")
                }
            }
            signInCallback(null)
        }
    }

    private fun createUserFromFirebaseUser(firebaseUser: FirebaseUser): User {
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = firebaseUser.displayName ?: "",
        )
    }

    override suspend fun signIn(): User? {
        // This method won't be used directly on Android; we use the activity result API instead
        return null
    }

    override suspend fun getCurrentFirebaseToken(): String? {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d("GoogleAuth", "Getting Firebase token for user: ${currentUser.email}")
                val tokenResult = currentUser.getIdToken(false).await()
                val token = tokenResult.token
                Log.d("GoogleAuth", "Firebase token obtained successfully")
                token
            } else {
                Log.w("GoogleAuth", "No current Firebase user found")
                null
            }
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Error getting Firebase token: ${e.message}", e)
            null
        }
    }

    override suspend fun signOut() {
        Log.d("GoogleSignIn", "Signing out user")

        try {
            auth.signOut()
            signInClient.signOut().await()
            isSignedInFlow.value = false
            Log.d("GoogleSignIn", "User signed out successfully")
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Error during sign out: ${e.message}", e)
            isSignedInFlow.value = false
        }
    }

    override fun isSignedIn(): Flow<Boolean> = isSignedInFlow

    override suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        Log.d("GoogleSignIn", "Getting current user: ${firebaseUser.email}")
        return createUserFromFirebaseUser(firebaseUser)
    }
}
