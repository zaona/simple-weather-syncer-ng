package com.application.zaona.weather.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * 图片处理工具
 * 提供压暗和模糊两种效果，用于手表端背景图预处理
 */
object ImageProcessingUtil {

    /**
     * 对 Bitmap 应用压暗效果
     * @param bitmap  原始图片
     * @param strength 压暗强度 0-100，0 为不处理
     * @return 处理后的新 Bitmap
     */
    fun applyDarken(bitmap: Bitmap, strength: Int): Bitmap {
        if (strength <= 0) return bitmap

        val alpha = (strength * 255 / 100).coerceIn(0, 255)
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = (alpha shl 24) or 0x000000 // 半透明黑色
        }
        canvas.drawRect(Rect(0, 0, result.width, result.height), paint)
        return result
    }

    /**
     * 对 Bitmap 应用盒式模糊（3 次迭代逼近高斯模糊）
     * @param bitmap 原始图片
     * @param radius 模糊半径 0-100，0 为不处理
     * @return 处理后的新 Bitmap
     */
    fun applyBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        repeat(3) { boxBlur(pixels, w, h, radius) }

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val sums = IntArray(w)

        // 水平方向
        for (y in 0 until h) {
            val rowOffset = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0

            // 初始化窗口 [0 - r, 0 + r]
            for (kx in -r..r) {
                val px = pixels[rowOffset + kx.coerceIn(0, w - 1)]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }

            for (x in 0 until w) {
                sums[x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)

                val leftPx = pixels[rowOffset + (x - r).coerceIn(0, w - 1)]
                val rightPx = pixels[rowOffset + (x + r + 1).coerceIn(0, w - 1)]
                rSum += ((rightPx shr 16) and 0xFF) - ((leftPx shr 16) and 0xFF)
                gSum += ((rightPx shr 8) and 0xFF) - ((leftPx shr 8) and 0xFF)
                bSum += (rightPx and 0xFF) - (leftPx and 0xFF)
            }

            for (x in 0 until w) {
                pixels[rowOffset + x] = sums[x]
            }
        }

        // 垂直方向
        val vSums = IntArray(h)
        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0

            // 初始化窗口 [0 - r, 0 + r]
            for (ky in -r..r) {
                val px = pixels[ky.coerceIn(0, h - 1) * w + x]
                rSum += (px shr 16) and 0xFF
                gSum += (px shr 8) and 0xFF
                bSum += px and 0xFF
            }

            for (y in 0 until h) {
                vSums[y] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)

                val topPx = pixels[(y - r).coerceIn(0, h - 1) * w + x]
                val bottomPx = pixels[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rSum += ((bottomPx shr 16) and 0xFF) - ((topPx shr 16) and 0xFF)
                gSum += ((bottomPx shr 8) and 0xFF) - ((topPx shr 8) and 0xFF)
                bSum += (bottomPx and 0xFF) - (topPx and 0xFF)
            }

            for (y in 0 until h) {
                pixels[y * w + x] = vSums[y]
            }
        }
    }
}
