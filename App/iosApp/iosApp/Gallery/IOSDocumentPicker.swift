import Foundation
import UIKit
import UniformTypeIdentifiers
import shared

/**
 * System document picker for course materials (PDF, PowerPoint, text,
 * Markdown, images). The picked file is copied into the app's temp
 * directory and handed to Kotlin through IOSFilePickerHandler.
 */
@MainActor
final class IOSDocumentPicker: NSObject, UIDocumentPickerDelegate {
    static let shared = IOSDocumentPicker()

    private var onPicked: ((String, String, String?) -> Void)?
    private var onCancelled: (() -> Void)?

    static let contentTypes: [UTType] = [
        .pdf,
        UTType("org.openxmlformats.presentationml.presentation") ?? .data,
        .plainText,
        UTType("net.daringfireball.markdown") ?? .plainText,
        .jpeg,
        .png,
    ]

    /** Register the handler the shared FilePicker calls. */
    static func register() {
        IOSFilePickerHandler.shared.handler = { onPicked, onCancelled, mimeTypes in
            // Map the requested MIME types (e.g. text/calendar for .ics) to UTTypes
            let requested = mimeTypes.compactMap { UTType(mimeType: $0) }
            DispatchQueue.main.async {
                IOSDocumentPicker.shared.pick(
                    contentTypes: requested.isEmpty ? IOSDocumentPicker.contentTypes : requested,
                    onPicked: { path, name, mime in _ = onPicked(path, name, mime) },
                    onCancelled: { _ = onCancelled() }
                )
            }
        }
    }

    func pick(contentTypes: [UTType], onPicked: @escaping (String, String, String?) -> Void, onCancelled: @escaping () -> Void) {
        self.onPicked = onPicked
        self.onCancelled = onCancelled

        let picker = UIDocumentPickerViewController(forOpeningContentTypes: contentTypes, asCopy: true)
        picker.allowsMultipleSelection = false
        picker.delegate = self

        guard let presenter = topViewController() else {
            print("❌ IOSDocumentPicker: no view controller to present from")
            finishCancelled()
            return
        }
        presenter.present(picker, animated: true)
    }

    // MARK: - UIDocumentPickerDelegate

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else {
            finishCancelled()
            return
        }
        let name = url.lastPathComponent
        let safeName = name.replacingOccurrences(of: "[^A-Za-z0-9._-]", with: "_", options: .regularExpression)
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent("picked", isDirectory: true)
            .appendingPathComponent("\(Int(Date().timeIntervalSince1970))_\(safeName)")
        do {
            try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.copyItem(at: url, to: destination)
        } catch {
            print("❌ IOSDocumentPicker: copy failed: \(error.localizedDescription)")
            finishCancelled()
            return
        }
        let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
        let callback = onPicked
        clear()
        callback?(destination.path, name, mime)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finishCancelled()
    }

    // MARK: - Helpers

    private func finishCancelled() {
        let callback = onCancelled
        clear()
        callback?()
    }

    private func clear() {
        onPicked = nil
        onCancelled = nil
    }

    private func topViewController() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              var top = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController ?? scene.windows.first?.rootViewController else {
            return nil
        }
        while let presented = top.presentedViewController { top = presented }
        return top
    }
}
