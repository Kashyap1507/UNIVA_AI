import Foundation
import UIKit

/**
 * iOS PDF Generator using UIGraphicsPDFRenderer
 * Generates actual PDF files from text content
 */
@objc public class IOSPdfGenerator: NSObject {

    // PDF page dimensions (A4 size at 72 DPI)
    private static let pageWidth: CGFloat = 595
    private static let pageHeight: CGFloat = 842
    private static let margin: CGFloat = 40
    private static let lineHeight: CGFloat = 20
    private static let fontSize: CGFloat = 12

    /**
     * Generates a PDF from text content
     * Returns NSData containing the PDF document
     */
    @objc public static func generatePdf(content: String) -> Data? {
        do {
            // Create PDF renderer
            let pdfMetaData = [
                kCGPDFContextCreator: "Univa AI",
                kCGPDFContextAuthor: "Univa AI",
                kCGPDFContextTitle: "Univa AI"
            ]
            let format = UIGraphicsPDFRendererFormat()
            format.documentInfo = pdfMetaData as [String: Any]

            let pageRect = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)
            let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)

            // Generate PDF data
            let pdfData = renderer.pdfData { context in
                // Split content into lines
                let lines = content.components(separatedBy: .newlines)

                var currentY: CGFloat = margin
                var pageNumber = 1

                // Start first page
                context.beginPage()

                // Configure text attributes
                let textFont = UIFont.systemFont(ofSize: fontSize)
                let paragraphStyle = NSMutableParagraphStyle()
                paragraphStyle.alignment = .left
                paragraphStyle.lineSpacing = 4

                let textAttributes: [NSAttributedString.Key: Any] = [
                    .font: textFont,
                    .paragraphStyle: paragraphStyle
                ]

                // Draw each line
                for line in lines {
                    // Check if we need a new page
                    if currentY + lineHeight > pageHeight - margin {
                        // Start new page
                        context.beginPage()
                        currentY = margin
                        pageNumber += 1
                    }

                    // Draw the line
                    let textRect = CGRect(
                        x: margin,
                        y: currentY,
                        width: pageWidth - (margin * 2),
                        height: lineHeight
                    )

                    line.draw(in: textRect, withAttributes: textAttributes)
                    currentY += lineHeight
                }
            }

            print("✅ IOSPdfGenerator: PDF generated successfully (\(pdfData.count) bytes)")
            return pdfData

        } catch {
            print("❌ IOSPdfGenerator: Failed to generate PDF: \(error.localizedDescription)")
            return nil
        }
    }

    /**
     * Saves PDF data to a file in the temporary directory
     * Returns the file URL on success
     */
    @objc public static func savePdfToFile(pdfData: Data, fileName: String) -> URL? {
        do {
            // Ensure fileName has .pdf extension
            var pdfFileName = fileName
            if !pdfFileName.hasSuffix(".pdf") {
                pdfFileName = "\(pdfFileName).pdf"
            }

            // Get temporary directory
            let tempDir = FileManager.default.temporaryDirectory
            let fileURL = tempDir.appendingPathComponent(pdfFileName)

            // Write PDF data to file
            try pdfData.write(to: fileURL, options: .atomic)

            print("✅ IOSPdfGenerator: PDF saved to \(fileURL.path)")
            return fileURL

        } catch {
            print("❌ IOSPdfGenerator: Failed to save PDF: \(error.localizedDescription)")
            return nil
        }
    }
}
