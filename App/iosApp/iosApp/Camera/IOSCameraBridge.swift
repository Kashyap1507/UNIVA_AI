import Foundation
import UIKit
import AVFoundation
import shared

/**
 * Swift bridge to connect IOSCameraController to Kotlin
 * Provides callback-based API for Kotlin to control iOS camera
 */
@MainActor
class IOSCameraBridge {

    private let cameraController = IOSCameraController.shared

    /**
     * Setup camera and get preview layer
     * Returns UIView containing camera preview
     */
    func setupCameraPreview(width: CGFloat, height: CGFloat) -> UIView {
        print("📸 IOSCameraBridge: Setting up camera preview (\(Int(width))x\(Int(height)))")

        // Setup camera
        cameraController.setupCamera()

        // Create container view for preview
        let previewView = UIView(frame: CGRect(x: 0, y: 0, width: width, height: height))
        previewView.backgroundColor = .black

        // Get preview layer and add to view
        if let previewLayer = cameraController.getPreviewLayer() {
            previewLayer.frame = previewView.bounds
            previewView.layer.addSublayer(previewLayer)
            print("✅ IOSCameraBridge: Preview layer added to view")
        } else {
            print("❌ IOSCameraBridge: Failed to get preview layer")
        }

        return previewView
    }

    /**
     * Start camera preview
     */
    func startPreview() {
        print("📸 IOSCameraBridge: Starting preview")
        cameraController.startPreview()
    }

    /**
     * Stop camera preview
     */
    func stopPreview() {
        print("📸 IOSCameraBridge: Stopping preview")
        cameraController.stopPreview()
    }

    /**
     * Capture photo at screen resolution
     * Matches Android CameraX implementation
     */
    func capturePhoto(completion: @escaping (String?, Error?) -> Void) {
        print("📸 IOSCameraBridge: Capturing photo")
        cameraController.capturePhoto { imagePath, error in
            if let error = error {
                print("❌ IOSCameraBridge: Photo capture failed - \(error.localizedDescription)")
                completion(nil, error)
            } else if let imagePath = imagePath {
                print("✅ IOSCameraBridge: Photo captured at \(imagePath)")
                completion(imagePath, nil)
            }
        }
    }

    /**
     * Set flash/torch enabled
     */
    func setFlashEnabled(_ enabled: Bool) {
        print("💡 IOSCameraBridge: Setting flash to \(enabled ? "ON" : "OFF")")
        cameraController.setFlashEnabled(enabled)
    }

    /**
     * Get flash enabled state
     */
    func isFlashEnabled() -> Bool {
        return cameraController.isFlashEnabled()
    }

    /// Check camera permission status.
    /// Must be nonisolated - iOS calls completion on background queue (com.apple.coremedia.capture.tccserver).
    nonisolated func checkCameraPermission(completion: @escaping @Sendable (Bool) -> Void) {
        print("🔐 IOSCameraBridge: Checking camera permission")

        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            print("✅ IOSCameraBridge: Camera permission granted")
            DispatchQueue.main.async {
                completion(true)
            }
        case .notDetermined:
            print("⚠️ IOSCameraBridge: Camera permission not determined, requesting...")
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    print(granted ? "✅ IOSCameraBridge: Permission granted" : "❌ IOSCameraBridge: Permission denied")
                    completion(granted)
                }
            }
        case .denied, .restricted:
            print("❌ IOSCameraBridge: Camera permission denied/restricted")
            DispatchQueue.main.async {
                completion(false)
            }
        @unknown default:
            print("❌ IOSCameraBridge: Unknown permission status")
            DispatchQueue.main.async {
                completion(false)
            }
        }
    }
}

/// Global singleton instance for Kotlin access.
/// nonisolated(unsafe) allows access from both MainActor and nonisolated contexts.
/// The permission check method is thread-safe (read-only system API calls).
private nonisolated(unsafe) var cameraBridgeInstance: IOSCameraBridge?

/// Initialize the camera bridge instance on the main actor.
@MainActor
private func getCameraBridgeInstance() -> IOSCameraBridge {
    if cameraBridgeInstance == nil {
        cameraBridgeInstance = IOSCameraBridge()
    }
    return cameraBridgeInstance!
}

/// Kotlin-accessible functions via IOSCallbackBridge.
@MainActor
@objc public class IOSCameraCallbacks: NSObject {

    @objc public static func setupCameraPreview(width: CGFloat, height: CGFloat) -> UIView {
        return getCameraBridgeInstance().setupCameraPreview(width: width, height: height)
    }

    @objc public static func startPreview() {
        getCameraBridgeInstance().startPreview()
    }

    @objc public static func stopPreview() {
        getCameraBridgeInstance().stopPreview()
    }

    @objc public static func capturePhoto(
        onSuccess: @escaping (String) -> Void,
        onError: @escaping (String) -> Void
    ) {
        getCameraBridgeInstance().capturePhoto { imagePath, error in
            if let error = error {
                onError(error.localizedDescription)
            } else if let imagePath = imagePath {
                onSuccess(imagePath)
            } else {
                onError("Unknown error during photo capture")
            }
        }
    }

    @objc public static func setFlashEnabled(_ enabled: Bool) {
        getCameraBridgeInstance().setFlashEnabled(enabled)
    }

    @objc public static func isFlashEnabled() -> Bool {
        return getCameraBridgeInstance().isFlashEnabled()
    }

    /// Check camera permission - nonisolated to allow iOS to call completion on background queue.
    @objc nonisolated public static func checkCameraPermission(completion: @escaping @Sendable (Bool) -> Void) {
        guard let instance = cameraBridgeInstance else {
            // Bridge not initialized, return false
            DispatchQueue.main.async {
                completion(false)
            }
            return
        }
        instance.checkCameraPermission(completion: completion)
    }
}
