import Foundation
import UIKit
import shared

/**
 * Bridge to expose Google Sign-In to Kotlin code
 * This is called from MainViewController.kt
 */
@MainActor
@objc public class IOSGoogleSignInBridge: NSObject {

    @objc public static let shared = IOSGoogleSignInBridge()

    private let authService: IOSGoogleAuthService

    private override init() {
        self.authService = IOSGoogleAuthService()
        super.init()
    }

    /**
     * Perform Google Sign-In from Kotlin
     * This is called when user clicks "Continue with Google" button
     * Returns Firebase ID token for backend authentication
     */
    @objc public func signInWithGoogle(completion: @escaping (String?, Error?) -> Void) {
        print("🔵 IOSGoogleSignInBridge: Sign-in requested from Kotlin")

        // Get the root view controller
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            print("❌ IOSGoogleSignInBridge: Could not get root view controller")
            let error = NSError(domain: "IOSGoogleSignInBridge", code: -1,
                              userInfo: [NSLocalizedDescriptionKey: "No root view controller"])
            completion(nil, error)
            return
        }

        // Find the top-most view controller
        var topController = rootViewController
        while let presented = topController.presentedViewController {
            topController = presented
        }

        print("🔵 IOSGoogleSignInBridge: Starting Google Sign-In with view controller: \(type(of: topController))")

        // Call the auth service
        authService.signIn(presenting: topController) { user in
            if let user = user {
                print("✅ IOSGoogleSignInBridge: Sign-in successful")
                print("   User ID: \(user.id)")
                print("   Email: \(user.email)")

                // Get Firebase ID token for backend authentication
                Task { @MainActor in
                    if let firebaseToken = await self.authService.getCurrentFirebaseToken() {
                        print("✅ IOSGoogleSignInBridge: Firebase token obtained")
                        print("   Token length: \(firebaseToken.count) characters")
                        completion(firebaseToken, nil)
                    } else {
                        print("❌ IOSGoogleSignInBridge: Failed to get Firebase token")
                        let error = NSError(domain: "IOSGoogleSignInBridge", code: -3,
                                          userInfo: [NSLocalizedDescriptionKey: "Failed to get Firebase token"])
                        completion(nil, error)
                    }
                }
            } else {
                print("❌ IOSGoogleSignInBridge: Sign-in failed or cancelled")
                let error = NSError(domain: "IOSGoogleSignInBridge", code: -2,
                                  userInfo: [NSLocalizedDescriptionKey: "Sign-in failed or cancelled"])
                completion(nil, error)
            }
        }
    }

    /**
     * Check if user is currently signed in
     */
    @objc public func isSignedIn() -> Bool {
        return authService.isSignedIn()
    }

    /**
     * Get current user if signed in
     */
    @objc public func getCurrentUser() -> shared.User? {
        return authService.getCurrentUser()
    }

    /**
     * Sign out current user
     */
    @objc public func signOut() {
        authService.signOut()
    }
}
