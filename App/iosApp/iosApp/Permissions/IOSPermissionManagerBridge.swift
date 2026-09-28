import Foundation
import UIKit
@preconcurrency import shared

/// Helper to wrap non-Sendable closures for Swift 6 concurrency.
private final class CallbackWrapper: @unchecked Sendable {
    let onGranted: () -> Void
    let onDenied: () -> Void

    init(onGranted: @escaping () -> Void, onDenied: @escaping () -> Void) {
        self.onGranted = onGranted
        self.onDenied = onDenied
    }
}

/// Swift bridge to connect IOSPermissionManager to Kotlin.
/// Provides callback-based API for Kotlin permission management.
@MainActor
@objc public class IOSPermissionManagerBridge: NSObject {

    // MARK: - Camera Permissions

    /// Check if camera permission is granted.
    @objc public static func isCameraPermissionGranted() -> Bool {
        return IOSPermissionManager.shared.isCameraPermissionGranted()
    }

    /// Request camera permission.
    @objc public static func requestCameraPermission(
        onGranted: @escaping () -> Void,
        onDenied: @escaping () -> Void
    ) {
        let wrapper = CallbackWrapper(onGranted: onGranted, onDenied: onDenied)
        IOSPermissionManager.shared.requestCameraPermission { granted in
            if granted {
                wrapper.onGranted()
            } else {
                wrapper.onDenied()
            }
        }
    }

    /// Check if camera permission was previously denied.
    @objc public static func wasCameraPermissionDenied() -> Bool {
        return IOSPermissionManager.shared.wasCameraPermissionDenied()
    }

    // MARK: - Photo Library Permissions

    /// Check if photo library permission is granted.
    @objc public static func isPhotoLibraryPermissionGranted() -> Bool {
        return IOSPermissionManager.shared.isPhotoLibraryPermissionGranted()
    }

    /// Request photo library permission.
    @objc public static func requestPhotoLibraryPermission(
        onGranted: @escaping () -> Void,
        onDenied: @escaping () -> Void
    ) {
        let wrapper = CallbackWrapper(onGranted: onGranted, onDenied: onDenied)
        IOSPermissionManager.shared.requestPhotoLibraryPermission { granted in
            if granted {
                wrapper.onGranted()
            } else {
                wrapper.onDenied()
            }
        }
    }

    /// Check if photo library permission was previously denied.
    @objc public static func wasPhotoLibraryPermissionDenied() -> Bool {
        return IOSPermissionManager.shared.wasPhotoLibraryPermissionDenied()
    }

    // MARK: - App Settings

    /// Open iOS Settings app to this app's permission page.
    @objc public static func openAppSettings() {
        IOSPermissionManager.shared.openAppSettings()
    }
}
