import Foundation
import AVFoundation
import UIKit

/**
 * iOS Camera Controller using AVFoundation
 * Matches Android CameraX implementation with exact screen resolution capture
 *
 * Features:
 * - Live camera preview
 * - Photo capture at exact screen resolution (matching Android)
 * - Flash/torch control
 * - Permission handling
 */
@MainActor
class IOSCameraController: NSObject {

    // Singleton instance
    static let shared = IOSCameraController()

    // Camera components
    private var captureSession: AVCaptureSession?
    private var videoDeviceInput: AVCaptureDeviceInput?
    private var photoOutput: AVCapturePhotoOutput?
    private var previewLayer: AVCaptureVideoPreviewLayer?

    // Photo capture callback
    private var photoCaptureCompletion: ((String?, Error?) -> Void)?

    // Target screen dimensions (like Android ImageAnalysis)
    private var targetScreenWidth: CGFloat = 0
    private var targetScreenHeight: CGFloat = 0

    // MARK: - Initialization

    private override init() {
        super.init()

        // Store target screen dimensions (including scale for retina displays)
        targetScreenWidth = UIScreen.main.bounds.width * UIScreen.main.scale
        targetScreenHeight = UIScreen.main.bounds.height * UIScreen.main.scale

        // UX-009: Comprehensive aspect ratio logging
        let screenAspectRatio = targetScreenWidth / targetScreenHeight
        let screenBounds = UIScreen.main.bounds
        let screenScale = UIScreen.main.scale

        print("📸 [Aspect Ratio] IOSCameraController: Initialized")
        print("📸 [Aspect Ratio] Screen bounds: \(Int(screenBounds.width))x\(Int(screenBounds.height))")
        print("📸 [Aspect Ratio] Screen scale: \(screenScale)x (Retina)")
        print("📸 [Aspect Ratio] Target resolution: \(Int(targetScreenWidth))x\(Int(targetScreenHeight))")
        print("📸 [Aspect Ratio] Target aspect ratio: \(String(format: "%.3f", screenAspectRatio))")
        print("📸 [Aspect Ratio] Device: \(UIDevice.current.model)")
    }

    // MARK: - Setup

    func setupCamera() {
        print("📸 IOSCameraController: Setting up camera...")

        let session = AVCaptureSession()
        session.beginConfiguration()

        // Set high quality preset
        if session.canSetSessionPreset(.photo) {
            session.sessionPreset = .photo
        }

        // Get back camera (wide angle)
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            print("❌ IOSCameraController: Failed to get back camera")
            session.commitConfiguration()
            return
        }

        // Add camera input
        do {
            let videoDeviceInput = try AVCaptureDeviceInput(device: videoDevice)

            if session.canAddInput(videoDeviceInput) {
                session.addInput(videoDeviceInput)
                self.videoDeviceInput = videoDeviceInput
                print("✅ IOSCameraController: Added camera input")
            } else {
                print("❌ IOSCameraController: Could not add camera input")
                session.commitConfiguration()
                return
            }
        } catch {
            print("❌ IOSCameraController: Error creating camera input: \(error.localizedDescription)")
            session.commitConfiguration()
            return
        }

        // Add photo output
        let photoOutput = AVCapturePhotoOutput()
        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
            self.photoOutput = photoOutput
            print("✅ IOSCameraController: Added photo output")
        } else {
            print("❌ IOSCameraController: Could not add photo output")
        }

        session.commitConfiguration()

        //Create preview layer
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill

        self.captureSession = session
        self.previewLayer = previewLayer

        print("✅ IOSCameraController: Camera setup complete")
    }

    // MARK: - Session Control

    func startPreview() {
        guard let session = captureSession, !session.isRunning else {
            print("⚠️ IOSCameraController: Session already running or not set up")
            return
        }

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
            print("✅ IOSCameraController: Preview started")
        }
    }

    func stopPreview() {
        guard let session = captureSession, session.isRunning else {
            print("⚠️ IOSCameraController: Session not running")
            return
        }

        DispatchQueue.global(qos: .userInitiated).async {
            session.stopRunning()
            print("⏸️ IOSCameraController: Preview stopped")
        }
    }

    // MARK: - Photo Capture

    func capturePhoto(completion: @escaping (String?, Error?) -> Void) {
        guard let photoOutput = photoOutput else {
            print("❌ IOSCameraController: Photo output not configured")
            completion(nil, NSError(domain: "IOSCameraController", code: -1, userInfo: [NSLocalizedDescriptionKey: "Photo output not configured"]))
            return
        }

        print("📸 IOSCameraController: Capturing photo...")

        // Store completion for delegate callback
        photoCaptureCompletion = completion

        // Configure photo settings
        let photoSettings = AVCapturePhotoSettings()
        photoSettings.flashMode = .off  // Will be controlled separately via torch

        // Capture photo
        photoOutput.capturePhoto(with: photoSettings, delegate: self)
    }

    // MARK: - Flash Control

    func setFlashEnabled(_ enabled: Bool) {
        guard let device = videoDeviceInput?.device, device.hasTorch else {
            print("⚠️ IOSCameraController: Device does not have torch")
            return
        }

        do {
            try device.lockForConfiguration()
            device.torchMode = enabled ? .on : .off
            device.unlockForConfiguration()
            print("💡 IOSCameraController: Flash \(enabled ? "enabled" : "disabled")")
        } catch {
            print("❌ IOSCameraController: Error setting flash: \(error.localizedDescription)")
        }
    }

    func isFlashEnabled() -> Bool {
        guard let device = videoDeviceInput?.device else { return false }
        return device.torchMode == .on
    }

    // MARK: - Preview Layer

    func getPreviewLayer() -> AVCaptureVideoPreviewLayer? {
        return previewLayer
    }

    // MARK: - Image Processing

    private func resizeImageToScreenResolution(_ image: UIImage) -> UIImage? {
        // Target size based on screen dimensions (matching Android behavior)
        let targetSize = CGSize(width: targetScreenWidth, height: targetScreenHeight)

        // UX-009: Calculate aspect ratios with comprehensive logging
        let imageAspectRatio = image.size.width / image.size.height
        let screenAspectRatio = targetSize.width / targetSize.height
        let aspectDiff = abs(imageAspectRatio - screenAspectRatio)

        print("📸 [Aspect Ratio] Image resizing")
        print("📸 [Aspect Ratio] Captured: \(Int(image.size.width))x\(Int(image.size.height))")
        print("📸 [Aspect Ratio] Captured aspect ratio: \(String(format: "%.3f", imageAspectRatio))")
        print("📸 [Aspect Ratio] Target: \(Int(targetSize.width))x\(Int(targetSize.height))")
        print("📸 [Aspect Ratio] Target aspect ratio: \(String(format: "%.3f", screenAspectRatio))")
        print("📸 [Aspect Ratio] Difference: \(String(format: "%.3f", aspectDiff))")

        // Warn if aspect ratios don't match closely
        if aspectDiff > 0.01 {
            print("⚠️ [Aspect Ratio] MISMATCH! Diff: \(String(format: "%.3f", aspectDiff)) - Cropping will occur")
        } else {
            print("✅ [Aspect Ratio] Match confirmed (within 0.01 tolerance)")
        }

        // Maintain aspect ratio with center cropping (like ContentScale.Crop)
        // Calculate scale to fill target size while maintaining aspect ratio
        let scaleX = targetSize.width / image.size.width
        let scaleY = targetSize.height / image.size.height
        let scale = max(scaleX, scaleY)  // Use max to fill the entire target area

        let scaledImageSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)

        // Calculate crop rect to center the scaled image
        let cropX = (scaledImageSize.width - targetSize.width) / 2
        let cropY = (scaledImageSize.height - targetSize.height) / 2

        print("   Scale factor: \(String(format: "%.3f", scale))")
        print("   Scaled size: \(Int(scaledImageSize.width))x\(Int(scaledImageSize.height))")
        print("   Crop offset: (\(Int(cropX)), \(Int(cropY)))")

        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { context in
            // Draw the image scaled and centered (cropped if necessary)
            let drawRect = CGRect(x: -cropX, y: -cropY, width: scaledImageSize.width, height: scaledImageSize.height)
            image.draw(in: drawRect)
        }
    }
}

// MARK: - AVCapturePhotoCaptureDelegate

extension IOSCameraController: AVCapturePhotoCaptureDelegate {

    nonisolated func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        // Extract image data from photo before entering MainActor context
        let imageData = photo.fileDataRepresentation()

        Task { @MainActor in
            if let error = error {
                print("❌ IOSCameraController: Photo capture failed: \(error.localizedDescription)")
                photoCaptureCompletion?(nil, error)
                return
            }

            guard let imageData = imageData,
                  let originalImage = UIImage(data: imageData) else {
                print("❌ IOSCameraController: Failed to get image data")
                let error = NSError(domain: "IOSCameraController", code: -2, userInfo: [NSLocalizedDescriptionKey: "Failed to get image data"])
                photoCaptureCompletion?(nil, error)
                return
            }

            print("✅ IOSCameraController: Photo captured")

            // Resize to screen resolution with aspect ratio maintenance
            guard let resizedImage = resizeImageToScreenResolution(originalImage) else {
                print("❌ IOSCameraController: Failed to resize image")
                let error = NSError(domain: "IOSCameraController", code: -3, userInfo: [NSLocalizedDescriptionKey: "Failed to resize image"])
                photoCaptureCompletion?(nil, error)
                return
            }

            // UX-009: Verify final aspect ratio matches screen
            let finalAspectRatio = resizedImage.size.width / resizedImage.size.height
            let screenAspectRatio = targetScreenWidth / targetScreenHeight
            let finalDiff = abs(finalAspectRatio - screenAspectRatio)

            print("📸 [Aspect Ratio] Final image: \(Int(resizedImage.size.width))x\(Int(resizedImage.size.height))")
            print("📸 [Aspect Ratio] Final aspect ratio: \(String(format: "%.3f", finalAspectRatio))")
            print("📸 [Aspect Ratio] Expected aspect ratio: \(String(format: "%.3f", screenAspectRatio))")
            print("📸 [Aspect Ratio] Final difference: \(String(format: "%.4f", finalDiff))")

            if finalDiff > 0.001 {
                print("⚠️ [Aspect Ratio] FINAL MISMATCH! This may cause distortion in PhotoReview")
            } else {
                print("✅ [Aspect Ratio] Final verification PASSED - exact screen aspect ratio maintained")
            }

            // Save to temporary file
            let filename = "captured_\(Date().timeIntervalSince1970).jpg"
            let tempDir = NSTemporaryDirectory()
            let filePath = (tempDir as NSString).appendingPathComponent(filename)

            // Convert to JPEG with high quality
            guard let jpegData = resizedImage.jpegData(compressionQuality: 0.95) else {
                print("❌ IOSCameraController: Failed to convert to JPEG")
                let error = NSError(domain: "IOSCameraController", code: -4, userInfo: [NSLocalizedDescriptionKey: "Failed to convert to JPEG"])
                photoCaptureCompletion?(nil, error)
                return
            }

            do {
                try jpegData.write(to: URL(fileURLWithPath: filePath))
                print("✅ IOSCameraController: Photo saved to: \(filePath)")
                print("   File size: \(jpegData.count / 1024) KB")
                photoCaptureCompletion?(filePath, nil)
            } catch {
                print("❌ IOSCameraController: Failed to save photo: \(error.localizedDescription)")
                photoCaptureCompletion?(nil, error)
            }
        }
    }
}
