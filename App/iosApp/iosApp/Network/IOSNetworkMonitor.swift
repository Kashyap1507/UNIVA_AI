import Foundation
import Network
import shared

/**
 * Monitors network connectivity and triggers sync when connection is restored
 *
 * Features:
 * - Real-time network status monitoring
 * - Network type detection (WiFi, Cellular, Ethernet)
 * - Cellular sync control based on user settings
 * - Automatic document sync when network becomes available
 * - Efficient with system callbacks (no polling)
 *
 * iOS equivalent of Android NetworkMonitor with NetworkDetector functionality
 */
@MainActor
@objc public class IOSNetworkMonitor: NSObject {

    @objc public static let shared = IOSNetworkMonitor()

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "NetworkMonitor")

    @objc public private(set) var isOnline: Bool = false
    private var currentPath: NWPath?

    private override init() {
        super.init()
        startMonitoring()
    }

    /**
     * Start monitoring network connectivity
     */
    private func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }

            Task { @MainActor [weak self] in
                guard let self = self else { return }

                let wasOffline = !self.isOnline
                let isNowOnline = path.status == .satisfied

                self.isOnline = isNowOnline
                self.currentPath = path

                print("📡 iOS NetworkMonitor: Network status changed")
                print("   Status: \(path.status)")
                print("   Is Online: \(isNowOnline)")
                if isNowOnline {
                    print("   Network Type: \(self.getNetworkTypeInternal(path: path))")
                }

                // If we just came online, trigger sync
                if wasOffline && isNowOnline {
                    print("🔄 iOS NetworkMonitor: Device came online - triggering document sync")
                    self.triggerSync()
                }
            }
        }

        monitor.start(queue: queue)
        print("🌐 iOS NetworkMonitor: Started monitoring")
    }

    /**
     * Trigger document sync to backend
     *
     * This is called automatically when network becomes available,
     * ensuring offline-created documents are uploaded as soon as possible.
     */
    private func triggerSync() {
        // TODO: Call BootstrapSyncCoordinator.smartSync() to trigger sync
        // This will be implemented once the sync bridge is fully operational
        print("📤 iOS NetworkMonitor: Sync triggered (bridge not yet implemented)")

        // For now, just log that we detected connectivity
        // Future implementation:
        // BootstrapSyncCoordinator.smartSync(force: false)
    }

    /**
     * Check if device is currently online
     */
    @objc public func isCurrentlyOnline() -> Bool {
        return isOnline
    }

    /**
     * Check if device is currently on WiFi
     */
    @objc public func isOnWiFi() -> Bool {
        guard let path = currentPath, path.status == .satisfied else {
            return false
        }
        return path.usesInterfaceType(.wifi)
    }

    /**
     * Check if device is currently on Cellular
     */
    @objc public func isOnCellular() -> Bool {
        guard let path = currentPath, path.status == .satisfied else {
            return false
        }
        return path.usesInterfaceType(.cellular)
    }

    /**
     * Check if device is on Ethernet
     */
    @objc public func isOnEthernet() -> Bool {
        guard let path = currentPath, path.status == .satisfied else {
            return false
        }
        return path.usesInterfaceType(.wiredEthernet)
    }

    /**
     * Get human-readable network type
     */
    @objc public func getNetworkType() -> String {
        guard let path = currentPath else {
            return "Offline"
        }
        return getNetworkTypeInternal(path: path)
    }

    /**
     * Internal helper to get network type from path
     */
    private func getNetworkTypeInternal(path: NWPath) -> String {
        if path.status != .satisfied {
            return "Offline"
        }

        if path.usesInterfaceType(.wifi) {
            return "WiFi"
        } else if path.usesInterfaceType(.cellular) {
            return "Cellular"
        } else if path.usesInterfaceType(.wiredEthernet) {
            return "Ethernet"
        } else {
            return "Unknown"
        }
    }

    /**
     * Check if current network is metered (cellular or expensive)
     *
     * iOS uses path.isExpensive to indicate metered connections
     * Cellular is always considered metered
     */
    @objc public func isMeteredConnection() -> Bool {
        guard let path = currentPath, path.status == .satisfied else {
            return true // Safe default: assume metered when offline
        }

        // Cellular is always metered
        if path.usesInterfaceType(.cellular) {
            return true
        }

        // Check if path is marked as expensive (e.g., personal hotspot, roaming WiFi)
        return path.isExpensive
    }

    /**
     * Check if sync should proceed based on network conditions
     *
     * @param cellularSyncEnabled User's cellular sync setting
     * @return true if sync can proceed, false if should wait for WiFi
     */
    @objc public func shouldSync(cellularSyncEnabled: Bool) -> Bool {
        if !isOnline {
            print("🔴 iOS NetworkMonitor: Sync blocked - Device is offline")
            return false
        }

        if isOnCellular() && !cellularSyncEnabled {
            print("🔴 iOS NetworkMonitor: Sync blocked - On cellular and cellular sync disabled")
            return false
        }

        print("✅ iOS NetworkMonitor: Sync allowed - Connected via \(getNetworkType())")
        return true
    }

    /**
     * Stop monitoring (cleanup)
     */
    @objc public func cleanup() {
        monitor.cancel()
        print("🧹 iOS NetworkMonitor: Stopped monitoring")
    }
}
