import Foundation
import UIKit
import AuthenticationServices
import FirebaseCore
import FirebaseAuth
@preconcurrency import shared

/**
 * iOS implementation of Apple Sign-In with Firebase Auth
 * Matches IOSGoogleAuthService functionality for consistency
 */
@MainActor
class IOSAppleAuthService: NSObject {

    // MARK: - Properties

    private let auth: Auth
    private var currentUser: FirebaseAuth.User?
    private var currentNonce: String?

    // MARK: - Initialization

    override init() {
        self.auth = Auth.auth()
        self.currentUser = auth.currentUser
        super.init()

        print("🍎 IOSAppleAuthService: Initialized")
        if let user = currentUser {
            print("🍎 Current user: \(user.email ?? "unknown") (\(user.uid))")
        }
    }

    // MARK: - Sign In

    func signIn(presenting viewController: UIViewController, completion: @escaping (shared.User?) -> Void) {
        print("🔐 IOSAppleAuthService: Starting Apple Sign-In...")

        // Generate nonce for security
        let nonce = randomNonceString()
        currentNonce = nonce

        let appleIDProvider = ASAuthorizationAppleIDProvider()
        let request = appleIDProvider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)

        let authorizationController = ASAuthorizationController(authorizationRequests: [request])
        authorizationController.delegate = self
        authorizationController.presentationContextProvider = viewController as? ASAuthorizationControllerPresentationContextProviding
        authorizationController.performRequests()

        // Store completion handler for delegate callback
        self.signInCompletion = completion
    }

    // Store completion handler for delegate callbacks
    private var signInCompletion: ((shared.User?) -> Void)?

    // MARK: - Get Firebase Token

    func getCurrentFirebaseToken() async -> String? {
        // Access auth.currentUser on MainActor
        guard let currentUser = auth.currentUser else {
            print("⚠️ IOSAppleAuthService: No current Firebase user")
            return nil
        }

        // Call getIDToken with continuation to avoid sendability issues
        return await withCheckedContinuation { continuation in
            currentUser.getIDToken { token, error in
                if let error = error {
                    print("❌ IOSAppleAuthService: Error getting Firebase token: \(error.localizedDescription)")
                    continuation.resume(returning: nil)
                } else if let token = token {
                    print("✅ IOSAppleAuthService: Firebase token obtained successfully")
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    // MARK: - Sign Out

    func signOut() {
        do {
            try auth.signOut()
            currentUser = nil
            print("✅ IOSAppleAuthService: User signed out successfully")
        } catch {
            print("❌ IOSAppleAuthService: Error signing out: \(error.localizedDescription)")
        }
    }

    // MARK: - Get Current User

    func getCurrentUser() -> shared.User? {
        // Access auth in a thread-safe way
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

    nonisolated func isSignedIn() -> Bool {
        return MainActor.assumeIsolated({ auth.currentUser != nil })
    }

    // MARK: - Helper Methods

    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length

        while remainingLength > 0 {
            let randoms: [UInt8] = (0 ..< 16).map { _ in
                var random: UInt8 = 0
                let errorCode = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                if errorCode != errSecSuccess {
                    fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
                }
                return random
            }

            randoms.forEach { random in
                if remainingLength == 0 {
                    return
                }

                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }

        return result
    }

    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        let hashString = hashedData.compactMap {
            String(format: "%02x", $0)
        }.joined()

        return hashString
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension IOSAppleAuthService: ASAuthorizationControllerDelegate {

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
            guard let nonce = currentNonce else {
                print("❌ IOSAppleAuthService: Invalid state - nonce is nil")
                signInCompletion?(nil)
                return
            }

            guard let appleIDToken = appleIDCredential.identityToken else {
                print("❌ IOSAppleAuthService: Unable to fetch identity token")
                signInCompletion?(nil)
                return
            }

            guard let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
                print("❌ IOSAppleAuthService: Unable to serialize token string from data: \(appleIDToken.debugDescription)")
                signInCompletion?(nil)
                return
            }

            print("✅ IOSAppleAuthService: Apple Sign-In successful")

            // Create Firebase credential with Apple ID token
            let credential = OAuthProvider.credential(
                providerID: .apple,
                idToken: idTokenString,
                rawNonce: nonce
            )

            // Authenticate with Firebase
            auth.signIn(with: credential) { [weak self] authResult, error in
                guard let self = self else { return }

                if let error = error {
                    print("❌ IOSAppleAuthService: Firebase auth error: \(error.localizedDescription)")
                    MainActor.assumeIsolated {
                        self.signInCompletion?(nil)
                    }
                    return
                }

                guard let firebaseUser = authResult?.user else {
                    print("❌ IOSAppleAuthService: Firebase user is nil")
                    MainActor.assumeIsolated {
                        self.signInCompletion?(nil)
                    }
                    return
                }

                MainActor.assumeIsolated {
                    self.currentUser = firebaseUser

                    print("✅ IOSAppleAuthService: Firebase authentication successful")

                    // Register FCM token for push notifications
                    print("📲 IOSAppleAuthService: Registering FCM token...")
                    Task { @MainActor in
                        IOSFirebaseMessagingDelegate.shared.getCurrentTokenAndRegister()
                    }

                    // Create User model
                    let userModel = shared.User(
                        id: firebaseUser.uid,
                        name: firebaseUser.displayName ?? appleIDCredential.fullName?.givenName ?? "",
                        email: firebaseUser.email ?? ""
                    )

                    self.signInCompletion?(userModel)
                }
            }
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        print("❌ IOSAppleAuthService: Sign-in error: \(error.localizedDescription)")
        signInCompletion?(nil)
    }
}

// MARK: - SHA256 Helper

import CryptoKit

extension IOSAppleAuthService {
    private struct SHA256 {
        static func hash(data: Data) -> Data {
            let hashed = CryptoKit.SHA256.hash(data: data)
            return Data(hashed)
        }
    }
}
