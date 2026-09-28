import Foundation
import UIKit
import FirebaseCore
import FirebaseAuth
import GoogleSignIn
@preconcurrency import shared

/**
 * iOS implementation of Google Sign-In with Firebase Auth
 * Matches Android's AndroidGoogleAuthService functionality
 */
@MainActor
class IOSGoogleAuthService {

    // MARK: - Properties

    private let auth: Auth
    private var currentUser: FirebaseAuth.User?

    // MARK: - Initialization

    init() {
        self.auth = Auth.auth()
        self.currentUser = auth.currentUser

        print("📱 IOSGoogleAuthService: Initialized")
        if let user = currentUser {
            print("📱 Current user: \(user.email ?? "unknown") (\(user.uid))")
        }
    }

    // MARK: - Sign In

    func signIn(presenting viewController: UIViewController, completion: @escaping (shared.User?) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            print("❌ IOSGoogleAuthService: Firebase client ID not found")
            completion(nil)
            return
        }

        // Configure Google Sign-In
        let config = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.configuration = config

        print("🔐 IOSGoogleAuthService: Starting Google Sign-In...")

        // Start the sign-in flow
        GIDSignIn.sharedInstance.signIn(withPresenting: viewController) { [weak self] result, error in
            guard let self = self else { return }

            if let error = error {
                print("❌ IOSGoogleAuthService: Sign-in error: \(error.localizedDescription)")
                completion(nil)
                return
            }

            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                print("❌ IOSGoogleAuthService: Failed to get ID token")
                completion(nil)
                return
            }

            let accessToken = user.accessToken.tokenString

            print("✅ IOSGoogleAuthService: Google Sign-In successful for: \(user.profile?.email ?? "unknown")")

            // Authenticate with Firebase
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)

            self.auth.signIn(with: credential) { authResult, error in
                if let error = error {
                    print("❌ IOSGoogleAuthService: Firebase auth error: \(error.localizedDescription)")
                    completion(nil)
                    return
                }

                guard let firebaseUser = authResult?.user else {
                    print("❌ IOSGoogleAuthService: Firebase user is nil")
                    completion(nil)
                    return
                }

                self.currentUser = firebaseUser

                print("✅ IOSGoogleAuthService: Firebase authentication successful")

                // Register FCM token for push notifications
                print("📲 IOSGoogleAuthService: Registering FCM token...")
                Task { @MainActor in
                    IOSFirebaseMessagingDelegate.shared.getCurrentTokenAndRegister()
                }

                // Create User model
                let userModel = shared.User(
                    id: firebaseUser.uid,
                    name: firebaseUser.displayName ?? "",
                    email: firebaseUser.email ?? ""
                )

                completion(userModel)
            }
        }
    }

    // MARK: - Get Firebase Token

    func getCurrentFirebaseToken() async -> String? {
        guard let currentUser = auth.currentUser else {
            print("⚠️ IOSGoogleAuthService: No current Firebase user")
            return nil
        }

        do {
            let token = try await currentUser.getIDToken()
            print("✅ IOSGoogleAuthService: Firebase token obtained successfully")
            return token
        } catch {
            print("❌ IOSGoogleAuthService: Error getting Firebase token: \(error.localizedDescription)")
            return nil
        }
    }

    // MARK: - Sign Out

    func signOut() {
        do {
            try auth.signOut()
            GIDSignIn.sharedInstance.signOut()
            currentUser = nil
            print("✅ IOSGoogleAuthService: User signed out successfully")
        } catch {
            print("❌ IOSGoogleAuthService: Error signing out: \(error.localizedDescription)")
        }
    }

    // MARK: - Get Current User

    func getCurrentUser() -> shared.User? {
        guard let firebaseUser = auth.currentUser else {
            return nil
        }

        return shared.User(
            id: firebaseUser.uid,
            name: firebaseUser.displayName ?? "",
            email: firebaseUser.email ?? ""
        )
    }

    // MARK: - Is Signed In

    func isSignedIn() -> Bool {
        return auth.currentUser != nil
    }
}
