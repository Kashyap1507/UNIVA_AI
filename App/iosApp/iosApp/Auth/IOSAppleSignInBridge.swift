import Foundation
import UIKit
import AuthenticationServices
@preconcurrency import shared

/**
 * Bridge between Kotlin and Swift for Apple Sign-In
 * Provides a simple interface for Kotlin code to trigger Apple authentication
 * Mirrors IOSGoogleSignInBridge structure for consistency
 */
@MainActor
class IOSAppleSignInBridge: NSObject {

    // MARK: - Singleton

    static let shared = IOSAppleSignInBridge()

    // MARK: - Properties

    private let authService: IOSAppleAuthService

    // MARK: - Initialization

    private override init() {
        self.authService = IOSAppleAuthService()
        super.init()
        print("🍎 IOSAppleSignInBridge: Initialized")
    }

    // MARK: - Sign In

    /**
     * Initiates Apple Sign-In flow and returns Firebase ID token
     * This token can be exchanged with the backend for a Wizly JWT token
     *
     * @param completion Callback with Firebase token (success) or error message (failure)
     */
    func signInWithApple(completion: @escaping @Sendable (String?, Error?) -> Void) {
        print("🔐 IOSAppleSignInBridge: signInWithApple() called")

        // Get the root view controller to present Apple Sign-In sheet
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            print("❌ IOSAppleSignInBridge: Unable to get root view controller")
            let error = NSError(
                domain: "IOSAppleSignInBridge",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Unable to present Apple Sign-In - no root view controller"]
            )
            completion(nil, error)
            return
        }

        // Find the top-most view controller (could be inside navigation stack)
        let presentingViewController = findTopViewController(from: rootViewController)

        print("📱 IOSAppleSignInBridge: Presenting Apple Sign-In from: \(type(of: presentingViewController))")

        // Start Apple Sign-In flow
        authService.signIn(presenting: presentingViewController) { user in
            if let user = user {
                print("✅ IOSAppleSignInBridge: Sign-in successful for user: \(user.email)")

                // Get Firebase ID token for backend authentication
                Task { @MainActor in
                    let firebaseToken = await self.authService.getCurrentFirebaseToken()

                    if let token = firebaseToken {
                        print("✅ IOSAppleSignInBridge: Firebase token retrieved successfully")
                        print("   Token length: \(token.count) characters")
                        completion(token, nil)
                    } else {
                        print("❌ IOSAppleSignInBridge: Failed to get Firebase token")
                        let error = NSError(
                            domain: "IOSAppleSignInBridge",
                            code: -2,
                            userInfo: [NSLocalizedDescriptionKey: "Failed to retrieve Firebase token"]
                        )
                        completion(nil, error)
                    }
                }
            } else {
                print("❌ IOSAppleSignInBridge: Sign-in failed - user is nil")
                let error = NSError(
                    domain: "IOSAppleSignInBridge",
                    code: -3,
                    userInfo: [NSLocalizedDescriptionKey: "Apple Sign-In failed or was cancelled"]
                )
                completion(nil, error)
            }
        }
    }

    // MARK: - Sign Out

    func signOut() {
        print("🔓 IOSAppleSignInBridge: Signing out")
        authService.signOut()
    }

    // MARK: - Get Current User

    func getCurrentUser() -> shared.User? {
        return authService.getCurrentUser()
    }

    // MARK: - Is Signed In

    nonisolated func isSignedIn() -> Bool {
        return authService.isSignedIn()
    }

    // MARK: - Helper Methods

    /**
     * Finds the top-most view controller in the hierarchy
     * Necessary because we might be presenting from within a navigation stack
     */
    private func findTopViewController(from viewController: UIViewController) -> UIViewController {
        if let presented = viewController.presentedViewController {
            return findTopViewController(from: presented)
        }

        if let navigationController = viewController as? UINavigationController {
            if let topViewController = navigationController.topViewController {
                return findTopViewController(from: topViewController)
            }
        }

        if let tabBarController = viewController as? UITabBarController {
            if let selectedViewController = tabBarController.selectedViewController {
                return findTopViewController(from: selectedViewController)
            }
        }

        return viewController
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension UIViewController: ASAuthorizationControllerPresentationContextProviding {
    public func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return self.view.window ?? ASPresentationAnchor()
    }
}
