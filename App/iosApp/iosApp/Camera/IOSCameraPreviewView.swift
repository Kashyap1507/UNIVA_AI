import Foundation
import AVFoundation
import UIKit
import SwiftUI

/**
 * iOS Camera Preview using AVFoundation
 * Provides live camera feed for scanning documents
 */
@objc(IOSCameraPreviewController)
public class IOSCameraPreviewController: UIViewController {

    // Camera session
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var videoDeviceInput: AVCaptureDeviceInput?

    // MARK: - Lifecycle

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
    }

    public override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        startSession()
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopSession()
    }

    // MARK: - Camera Setup

    private func setupCamera() {
        print("📸 IOSCameraPreview: Setting up camera...")

        // Check camera authorization
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureCaptureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted {
                    DispatchQueue.main.async {
                        self?.configureCaptureSession()
                    }
                } else {
                    print("❌ IOSCameraPreview: Camera permission denied")
                }
            }
        case .denied, .restricted:
            print("❌ IOSCameraPreview: Camera permission denied or restricted")
            showPermissionDeniedAlert()
        @unknown default:
            print("❌ IOSCameraPreview: Unknown authorization status")
        }
    }

    private func configureCaptureSession() {
        print("📸 IOSCameraPreview: Configuring capture session...")

        let session = AVCaptureSession()
        session.beginConfiguration()

        // Set session preset for high quality
        if session.canSetSessionPreset(.photo) {
            session.sessionPreset = .photo
        }

        // Get back camera
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            print("❌ IOSCameraPreview: Failed to get back camera")
            session.commitConfiguration()
            return
        }

        // Add camera input
        do {
            let videoDeviceInput = try AVCaptureDeviceInput(device: videoDevice)

            if session.canAddInput(videoDeviceInput) {
                session.addInput(videoDeviceInput)
                self.videoDeviceInput = videoDeviceInput
                print("✅ IOSCameraPreview: Added camera input")
            } else {
                print("❌ IOSCameraPreview: Could not add camera input to session")
                session.commitConfiguration()
                return
            }
        } catch {
            print("❌ IOSCameraPreview: Error creating camera input: \(error.localizedDescription)")
            session.commitConfiguration()
            return
        }

        session.commitConfiguration()

        // Create preview layer
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.frame = view.bounds
        view.layer.addSublayer(previewLayer)

        self.captureSession = session
        self.previewLayer = previewLayer

        print("✅ IOSCameraPreview: Camera setup complete")
    }

    private func startSession() {
        guard let session = captureSession, !session.isRunning else { return }

        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
            print("✅ IOSCameraPreview: Camera session started")
        }
    }

    private func stopSession() {
        guard let session = captureSession, session.isRunning else { return }

        DispatchQueue.global(qos: .userInitiated).async {
            session.stopRunning()
            print("⏸️ IOSCameraPreview: Camera session stopped")
        }
    }

    private func showPermissionDeniedAlert() {
        DispatchQueue.main.async {
            let alert = UIAlertController(
                title: "Camera Access Required",
                message: "Please enable camera access in Settings to scan documents.",
                preferredStyle: .alert
            )

            alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
            alert.addAction(UIAlertAction(title: "Settings", style: .default) { _ in
                if let settingsURL = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(settingsURL)
                }
            })

            self.present(alert, animated: true)
        }
    }

    // MARK: - Public Methods

    @objc public func setFlashEnabled(_ enabled: Bool) {
        guard let device = videoDeviceInput?.device, device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            device.torchMode = enabled ? .on : .off
            device.unlockForConfiguration()
            print("💡 IOSCameraPreview: Flash \(enabled ? "enabled" : "disabled")")
        } catch {
            print("❌ IOSCameraPreview: Error setting flash: \(error.localizedDescription)")
        }
    }
}

/**
 * SwiftUI wrapper for IOSCameraPreviewController
 */
struct IOSCameraPreviewView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> IOSCameraPreviewController {
        return IOSCameraPreviewController()
    }

    func updateUIViewController(_ uiViewController: IOSCameraPreviewController, context: Context) {
        // No updates needed
    }
}
