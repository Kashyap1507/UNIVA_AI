import Foundation
import Network

/**
 * iOS Network Detector Utility
 *
 * Detects network connectivity status and connection type (WiFi vs Cellular).
 * Used to implement cellular data sync control.
 *
 * Features:
 * - Check if device is online
 * - Detect network type (WiFi, Cellular, Wired, etc.)
 * - Check if currently on cellular data
 * - Support for iOS 12+ using Network framework
 *
 * Usage:
 * ```swift
 * let detector = IOSNetworkDetector.shared
 *
 * if !detector.isOnline() {
 *     // Handle offline state
 * } else if detector.isOnCellular() {
 *     // On cellular data - may want to skip large uploads
 * } else if detector.isOnWiFi() {
 *     // On WiFi - safe to upload large files
 * }
 * ```
 */
@MainActor
class IOSNetworkDetector {

    // MARK: - Singleton

    static let shared = IOSNetworkDetector()

    // MARK: - Properties

    private let monitor: NWPathMonitor
    private let monitorQueue = DispatchQueue(label: "com.kashyap.wizlyai.network.monitor")
    private var currentPath: NWPath?

    // MARK: - Initialization

    private init() {
        self.monitor = NWPathMonitor()

        // Start monitoring network changes
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor [weak self] in
                self?.currentPath = path
            }
            let status = path.status == .satisfied ? "connected" : "disconnected"
            print("📡 NetworkDetector: Network status changed to \(status)")
        }

        monitor.start(queue: monitorQueue)
    }

    deinit {
        monitor.cancel()
    }

    // MARK: - Public Methods

    /**
     * Check if device has any active network connection
     *
     * @return true if online, false if offline
     */
    func isOnline() -> Bool {
        guard let path = currentPath else {
            // If no path yet, try to get current status synchronously
            let pathMonitor = NWPathMonitor()
            let semaphore = DispatchSemaphore(value: 0)

            // Use a Sendable class to make the value thread-safe for concurrent access
            final class ConnectionStatus: @unchecked Sendable {
                var isConnected = false
            }
            let status = ConnectionStatus()

            pathMonitor.pathUpdateHandler = { path in
                status.isConnected = path.status == .satisfied
                semaphore.signal()
            }
            pathMonitor.start(queue: DispatchQueue.global())
            _ = semaphore.wait(timeout: .now() + 1.0)
            pathMonitor.cancel()

            return status.isConnected
        }

        return path.status == .satisfied
    }

    /**
     * Check if device is currently on WiFi
     *
     * @return true if connected to WiFi, false otherwise
     */
    func isOnWiFi() -> Bool {
        guard let path = currentPath else {
            return false
        }

        return path.usesInterfaceType(.wifi)
    }

    /**
     * Check if device is currently on cellular data
     *
     * Detects all types of cellular connections:
     * - 2G/EDGE
     * - 3G/HSPA
     * - 4G/LTE
     * - 5G
     *
     * @return true if connected via cellular, false otherwise
     */
    func isOnCellular() -> Bool {
        guard let path = currentPath else {
            return false
        }

        return path.usesInterfaceType(.cellular)
    }

    /**
     * Check if device is on Ethernet/Wired connection
     *
     * @return true if connected via Ethernet, false otherwise
     */
    func isOnEthernet() -> Bool {
        guard let path = currentPath else {
            return false
        }

        return path.usesInterfaceType(.wiredEthernet)
    }

    /**
     * Get human-readable network type string
     *
     * @return Network type name ("WiFi", "Cellular", "Ethernet", "Unknown", "Offline")
     */
    func getNetworkType() -> String {
        if !isOnline() {
            return "Offline"
        } else if isOnWiFi() {
            return "WiFi"
        } else if isOnCellular() {
            return "Cellular"
        } else if isOnEthernet() {
            return "Ethernet"
        } else {
            return "Unknown"
        }
    }

    /**
     * Check if current network is metered (cellular)
     *
     * iOS considers cellular connections as expensive/metered.
     *
     * @return true if network is metered (counts against data quota), false otherwise
     */
    func isMeteredConnection() -> Bool {
        guard let path = currentPath else {
            return true // Safe default: assume metered
        }

        return path.isExpensive
    }

    /**
     * Check if sync should proceed based on network conditions
     *
     * This is a convenience method that combines:
     * - Online check
     * - Cellular check
     * - Cellular sync setting
     *
     * @param cellularSyncEnabled User's cellular sync setting (true = allow cellular)
     * @return true if sync can proceed, false if should wait for WiFi
     */
    func shouldSync(cellularSyncEnabled: Bool) -> Bool {
        if !isOnline() {
            print("🚫 NetworkDetector: Sync blocked - Device is offline")
            return false
        }

        if isOnCellular() && !cellularSyncEnabled {
            print("🚫 NetworkDetector: Sync blocked - On cellular data and cellular sync disabled")
            return false
        }

        // Online and either on WiFi, or on cellular with sync enabled
        let networkType = getNetworkType()
        print("✅ NetworkDetector: Sync allowed - Connected via \(networkType)")
        return true
    }

    /**
     * Get detailed network info for logging/debugging
     *
     * @return Dictionary with network status details
     */
    func getNetworkInfo() -> [String: Any] {
        return [
            "online": isOnline(),
            "networkType": getNetworkType(),
            "onWiFi": isOnWiFi(),
            "onCellular": isOnCellular(),
            "onEthernet": isOnEthernet(),
            "isMetered": isMeteredConnection()
        ]
    }
}
