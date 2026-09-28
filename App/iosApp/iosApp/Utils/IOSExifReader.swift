import Foundation
import UIKit
import ImageIO

/**
 * iOS EXIF Reader
 * Reads EXIF orientation metadata from image files
 */
@MainActor
class IOSExifReader {

    static let shared = IOSExifReader()

    private init() {}

    /**
     * Get image orientation in degrees from EXIF data
     * @param filePath: Absolute path to image file
     * @return Rotation in degrees (0, 90, 180, or 270)
     */
    func getOrientation(filePath: String) -> Int {
        let fileURL = URL(fileURLWithPath: filePath)

        // Check if file exists
        guard FileManager.default.fileExists(atPath: filePath) else {
            print("⚠️ IOSExifReader: File does not exist - \(filePath)")
            return 0
        }

        // Read EXIF metadata
        guard let imageSource = CGImageSourceCreateWithURL(fileURL as CFURL, nil) else {
            print("❌ IOSExifReader: Failed to create image source")
            return 0
        }

        guard let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [String: Any] else {
            print("❌ IOSExifReader: Failed to read image properties")
            return 0
        }

        // Get orientation value (defaults to 1 = normal)
        let orientationValue = properties[kCGImagePropertyOrientation as String] as? Int ?? 1

        // Convert CGImagePropertyOrientation to degrees
        // Reference: https://developer.apple.com/documentation/imageio/cgimagepropertyorientation
        let degrees: Int
        switch orientationValue {
        case 1:  // Normal (0°)
            degrees = 0
        case 3:  // Rotated 180°
            degrees = 180
        case 6:  // Rotated 90° CW
            degrees = 90
        case 8:  // Rotated 270° CW (90° CCW)
            degrees = 270
        case 2:  // Flipped horizontally
            degrees = 0
        case 4:  // Flipped vertically
            degrees = 180
        case 5:  // Flipped horizontally + 90° CW
            degrees = 90
        case 7:  // Flipped horizontally + 270° CW
            degrees = 270
        default:
            degrees = 0
        }

        let fileName = (filePath as NSString).lastPathComponent
        print("📷 IOSExifReader: Orientation=\(orientationValue) → \(degrees)° (file: \(fileName))")

        return degrees
    }
}
