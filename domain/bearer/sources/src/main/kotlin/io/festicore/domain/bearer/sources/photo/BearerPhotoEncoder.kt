package io.festicore.domain.bearer.sources.photo

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import io.festicore.domain.bearer.entity.BearerError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * 업로드 전 사진을 서버 규격에 맞춘다: JPEG, 5MB 이하, 짧은 변 256px 이상. iOS `BearerPhotoEncoder`.
 * 원본 바이트는 그대로 내보내지 않고 픽셀만 다시 인코딩한다. `Bitmap.compress`는 EXIF를 쓰지 않으므로 위치 · 기기 · 시각 정보가 빠진다.
 * EXIF 방향은 픽셀에 굽는다: API 28+는 `ImageDecoder`가 적용하고, 26-27은 `BitmapFactory` + `ExifInterface`로 직접 돌린다.
 * [useImageDecoder]는 테스트가 26-27 경로를 고르려고 주입한다.
 */
internal class BearerPhotoEncoder(private val useImageDecoder: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    fun encode(data: ByteArray): ByteArray {
        val decoded = if (useImageDecoder) decodeWithImageDecoder(data) else decodeWithBitmapFactory(data)
        val bitmap = decoded.bitmap.scaledTo(decoded.plan)
        try {
            for (quality in QUALITIES) {
                val jpeg = bitmap.jpeg(quality)
                if (jpeg.size <= MAX_BYTES) return jpeg
            }
        } finally {
            bitmap.recycle()
        }
        throw BearerError.PhotoTooLarge()
    }

    @TargetApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(data: ByteArray): Decoded {
        var plan: SizePlan? = null
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, info, _ ->
                val sizePlan = SizePlan.of(info.size.width, info.size.height)
                plan = sizePlan
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sizePlan.sampleSize)
            }
        } catch (e: BearerError) {
            throw e
        } catch (_: IOException) { // ImageDecoder.DecodeException 포함
            throw BearerError.UnsupportedPhoto()
        } catch (_: IllegalArgumentException) {
            throw BearerError.UnsupportedPhoto()
        }
        return Decoded(bitmap, plan ?: throw BearerError.UnsupportedPhoto())
    }

    private fun decodeWithBitmapFactory(data: ByteArray): Decoded {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BearerError.UnsupportedPhoto()
        val plan = SizePlan.of(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: throw BearerError.UnsupportedPhoto()
        return Decoded(bitmap.oriented(exifOrientation(data)), plan)
    }

    private fun exifOrientation(data: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(data)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private class Decoded(
        val bitmap: Bitmap,
        val plan: SizePlan,
    )

    /**
     * 원본 크기에서 정한 목표 크기. 방향과 상관없이 긴 변 · 짧은 변으로만 본다.
     * 긴 변 = `min(긴 변, max(2048, ceil(256 × 긴 변 / 짧은 변)))` — 줄여도 짧은 변이 256px 밑으로 내려가지 않는다.
     */
    data class SizePlan(
        val longSide: Int,
        val shortSide: Int,
        /** 메모리를 아끼려고 목표 긴 변보다 작아지지 않는 만큼 2의 거듭제곱으로 줄여 디코딩한다. */
        val sampleSize: Int,
    ) {
        companion object {
            fun of(width: Int, height: Int): SizePlan {
                val long = max(width, height)
                val short = min(width, height)
                if (short < MIN_SIDE) throw BearerError.PhotoTooSmall()
                val keepsShortSide = ceilDiv(MIN_SIDE.toLong() * long, short.toLong())
                val targetLong = min(long.toLong(), max(MAX_SIDE.toLong(), keepsShortSide)).toInt()
                val targetShort = ceilDiv(short.toLong() * targetLong, long.toLong()).toInt()
                var sample = 1
                while (long / (sample * 2) >= targetLong) sample *= 2
                return SizePlan(targetLong, targetShort, sample)
            }

            private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
        }
    }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        const val MIN_SIDE = 256

        /** 긴 변을 이 크기로 줄인다. 서버의 해상도 상한과 업로드 시간을 함께 줄인다. */
        const val MAX_SIDE = 2048
        private val QUALITIES = listOf(85, 75, 65, 55, 45, 35)

        /** JPEG로 다시 인코딩한다. 인코딩에 실패하면 빈 · 부분 결과를 올리지 않고 지원하지 않는 사진으로 본다. */
        private fun Bitmap.jpeg(quality: Int): ByteArray {
            val output = ByteArrayOutputStream()
            if (!compress(Bitmap.CompressFormat.JPEG, quality, output) || output.size() == 0) {
                throw BearerError.UnsupportedPhoto()
            }
            return output.toByteArray()
        }

        /** 디코딩한(방향 적용) 비트맵을 [plan] 크기로 맞춘다. 가로 · 세로는 비트맵 방향을 따른다. */
        private fun Bitmap.scaledTo(plan: SizePlan): Bitmap {
            val (targetWidth, targetHeight) = if (width >= height) plan.longSide to plan.shortSide else plan.shortSide to plan.longSide
            if (width == targetWidth && height == targetHeight) return this
            return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true).also { if (it !== this) recycle() }
        }

        /** EXIF 방향 값대로 픽셀을 돌리거나 뒤집는다(Glide `TransformationUtils`와 같은 대응). */
        private fun Bitmap.oriented(orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.apply { setRotate(180f) }.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(90f) }.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-90f) }.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return this
            }
            return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { if (it !== this) recycle() }
        }
    }
}
