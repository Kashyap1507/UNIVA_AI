import Foundation
import UIKit
import AVFoundation
import Photos

/// iOS Permission Manager - Handles camera and photo library permissions.
@MainActor
class IOSPermissionManager {

    /// Shared instance - stored as nonisolated(unsafe) to allow access from nonisolated methods.
    /// The permission check methods are thread-safe (read-only system API calls).
    private nonisolated(unsafe) static var _shared: IOSPermissionManager?

    /// Thread-safe lazy getter for the shared instance.
    static var shared: IOSPermissionManager {
        if _shared == nil {
            _shared = IOSPermissionManager()
        }
        return _shared!
    }

    private init() {}

    // MARK: - Camera Permissions

    /// Check if camera permission is granted.
    func isCameraPermissionGranted() -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        let isGranted = status == .authorized
        print("🔐 IOSPermissionManager: Camera permission \(isGranted ? "GRANTED" : "NOT GRANTED") (status: \(status.rawValue))")
        return isGranted
    }

    /// Request camera permission.
    /// Must be nonisolated - iOS calls completion on background queue (com.apple.coremedia.capture.tccserver).
    nonisolated func requestCameraPermission(completion: @escaping @Sendable (Bool) -> Void) {
        let status = AVCaptureDevice.authorizationStatus(for: .video)

        switch status {
        case .authorized:
            print("✅ IOSPermissionManager: Camera already authorized")
            DispatchQueue.main.async {
                completion(true)
            }

        case .notDetermined:
            print("⚠️ IOSPermissionManager: Requesting camera permission...")
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    print(granted ? "✅ IOSPermissionManager: Camera permission GRANTED" : "❌ IOSPermissionManager: Camera permission DENIED")
                    completion(granted)
                }
            }

        case .denied, .restricted:
            print("❌ IOSPermissionManager: Camera permission denied/restricted")
            DispatchQueue.main.async {
                completion(false)
            }

        @unknown default:
            print("❌ IOSPermissionManager: Unknown camera permission status")
            DispatchQueue.main.async {
                completion(false)
            }
        }
    }

    /// Get camera permission status.
    nonisolated func getCameraPermissionStatus() -> AVAuthorizationStatus {
        return AVCaptureDevice.authorizationStatus(for: .video)
    }

    // MARK: - Photo Library Permissions

    /// Check if photo library permission is granted.
    nonisolated func isPhotoLibraryPermissionGranted() -> Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        let isGranted = status == .authorized || status == .limited
        print("🔐 IOSPermissionManager: Photo library permission \(isGranted ? "GRANTED" : "NOT GRANTED") (status: \(status.rawValue))")
        return isGranted
    }

    /// Request photo library permission (read/write).
    /// Must be nonisolated - iOS calls completion on background queue.
    nonisolated func requestPhotoLibraryPermission(completion: @escaping @Sendable (Bool) -> Void) {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)

        switch status {
        case .authorized, .limited:
            print("✅ IOSPermissionManager: Photo library already authorized")
            DispatchQueue.main.async {
                completion(true)
            }

        case .notDetermined:
            print("⚠️ IOSPermissionManager: Requesting photo library permission...")
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                let granted = newStatus == .authorized || newStatus == .limited
                DispatchQueue.main.async {
                    print(granted ? "✅ IOSPermissionManager: Photo library permission GRANTED" : "❌ IOSPermissionManager: Photo library permission DENIED")
                    completion(granted)
                }
            }

        case .denied, .restricted:
            print("❌ IOSPermissionManager: Photo library permission denied/restricted")
            DispatchQueue.main.async {
                completion(false)
            }

        @unknown default:
            print("❌ IOSPermissionManager: Unknown photo library permission status")
            DispatchQueue.main.async {
                completion(false)
            }
        }
    }

    /// Get photo library permission status.
    nonisolated func getPhotoLibraryPermissionStatus() -> PHAuthorizationStatus {
        return PHPhotoLibrary.authorizationStatus(for: .readWrite)
    }

    // MARK: - App Settings

    /// Open iOS Settings app to this app's permission page.
    func openAppSettings() {
        print("⚙️ IOSPermissionManager: Opening app settings...")

        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            print("❌ IOSPermissionManager: Failed to create settings URL")
            return
        }

        if UIApplication.shared.canOpenURL(settingsUrl) {
            UIApplication.shared.open(settingsUrl) { success in
                print(success ? "✅ IOSPermissionManager: Settings opened" : "❌ IOSPermissionManager: Failed to open settings")
            }
        } else {
            print("❌ IOSPermissionManager: Cannot open settings URL")
        }
    }

    // MARK: - Permission Denial Check

    /// Check if camera permission was previously denied (helps determine if we should show rationale).
    nonisolated func wasCameraPermissionDenied() -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        return status == .denied
    }

    /// Check if photo library permission was previously denied.
    nonisolated func wasPhotoLibraryPermissionDenied() -> Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .denied
    }
}
