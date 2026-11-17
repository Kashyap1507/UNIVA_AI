package com.kashyap.wizlyai.util

/**
 * Platform-agnostic PDF sharing manager
 * Shares text content (notes, feedback) as a .pdf file using native share sheets
 */
expect class PdfShareManager() {
    /**
     * Share text content as a PDF file
     * @param content The text content
     * @param fileName The name of the PDF file (without extension)
     */
    suspend fun sharePdf(content: String, fileName: String)
}
