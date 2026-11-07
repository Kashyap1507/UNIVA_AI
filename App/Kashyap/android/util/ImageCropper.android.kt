package com.kashyap.wizlyai.util

import android.content.Context
import android.graphics.*
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Android implementation of ImageCropper
 * Uses Android Canvas and Matrix for perspective transformation
 */
actual class ImageCropper(private val context: Context) {
    actual fun cropWithPerspective(
        inputPath: String,
        topLeft: Offset,
        topRight: Offset,
        bottomLeft: Offset,
        bottomRight: Offset,
    ): String? {
        try {
            // Load the input image
            val inputBitmap = BitmapFactory.decodeFile(inputPath) ?: return null
            val width = inputBitmap.width.toFloat()
            val height = inputBitmap.height.toFloat()

            // Convert normalized coordinates to pixel coordinates
            val srcPoints =
                floatArrayOf(
                    topLeft.x * width,
                    topLeft.y * height, // top-left
                    topRight.x * width,
                    topRight.y * height, // top-right
                    bottomLeft.x * width,
                    bottomLeft.y * height, // bottom-left
                    bottomRight.x * width,
                    bottomRight.y * height, // bottom-right
                )

            // Calculate output dimensions based on the quadrilateral
            val outputWidth =
                max(
                    distance(topLeft.x * width, topLeft.y * height, topRight.x * width, topRight.y * height),
                    distance(bottomLeft.x * width, bottomLeft.y * height, bottomRight.x * width, bottomRight.y * height),
                ).toInt()

            val outputHeight =
                max(
                    distance(topLeft.x * width, topLeft.y * height, bottomLeft.x * width, bottomLeft.y * height),
                    distance(topRight.x * width, topRight.y * height, bottomRight.x * width, bottomRight.y * height),
                ).toInt()

            // Destination points (rectangle)
            val dstPoints =
                floatArrayOf(
                    0f,
                    0f, // top-left
                    outputWidth.toFloat(),
                    0f, // top-right
                    0f,
                    outputHeight.toFloat(), // bottom-left
                    outputWidth.toFloat(),
                    outputHeight.toFloat(), // bottom-right
                )

            // Create perspective transform matrix
            val matrix = Matrix()
            matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

            // Create output bitmap with perspective transform
            val outputBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)
            canvas.drawBitmap(inputBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

            // Save to output file
            val outputFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Clean up
            inputBitmap.recycle()
            outputBitmap.recycle()

            return outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    actual fun cropRectangle(
        inputPath: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): String? {
        try {
            // Load the input image
            val inputBitmap = BitmapFactory.decodeFile(inputPath) ?: return null
            val width = inputBitmap.width
            val height = inputBitmap.height

            // Convert normalized coordinates to pixel coordinates
            val x = (left * width).toInt()
            val y = (top * height).toInt()
            val cropWidth = ((right - left) * width).toInt()
            val cropHeight = ((bottom - top) * height).toInt()

            // Create cropped bitmap
            val croppedBitmap = Bitmap.createBitmap(inputBitmap, x, y, cropWidth, cropHeight)

            // Save to output file
            val outputFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Clean up
            inputBitmap.recycle()
            croppedBitmap.recycle()

            return outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Calculate distance between two points
     */
    private fun distance(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
