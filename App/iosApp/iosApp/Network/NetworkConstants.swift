import Foundation

/**
 * Network configuration constants for iOS
 *
 * Provides API base URL for network requests.
 * TODO: This should be replaced with shared Kotlin NetworkConstants when available.
 */
@objc public class NetworkConstants: NSObject {

    @objc public static let shared = NetworkConstants()

    /**
     * Base URL for API requests
     *
     * Development: http://localhost:5000
     * Production: Update with production URL
     */
    @objc public let API_BASE_URL = "http://localhost:5000"

    private override init() {
        super.init()
    }
}
