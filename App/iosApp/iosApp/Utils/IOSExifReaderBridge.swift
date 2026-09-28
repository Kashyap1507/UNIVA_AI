import Foundation

/**
 * Bridge for exposing IOSExifReader to Kotlin
 */
@MainActor
@objc public class IOSExifReaderBridge: NSObject {

    private static let exifReader = IOSExifReader.shared

    /**
     * Get image orientation in degrees from EXIF data
     * @param filePath: Absolute path to image file
     * @return Rotation in degrees (0, 90, 180, or 270)
     */
    @objc public static func getOrientation(filePath: String) -> Int {
        return exifReader.getOrientation(filePath: filePath)
    }
}
