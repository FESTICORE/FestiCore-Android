package io.festicore.domain.bearer.sources

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagPhoto
import io.festicore.domain.bearer.entity.BearerPhotoUploadEvent
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.festicore.domain.bearer.sources.photo.BearerPhotoEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * iOS `BearerDomainTests` 사진 스트림 2건 · 사진 규격 3건(같은 이름) + Android 추가(EXIF · GPS 제거, EXIF 방향).
 * 실제 디코딩 · JPEG 인코딩이 필요해 Robolectric 네이티브 그래픽으로 돌린다. API 26-27 경로(`BitmapFactory` + `ExifInterface`)는 주입으로 고른다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BearerPhotoTest {
    @Test
    fun uploadsWithProgress() = runTest {
        val repository = PhotoRepository()

        val events = useCase(repository).uploadPhoto("flg_1", png(300, 300)).toList()

        val photo = BearerFlagPhoto(photoUrl = null, thumbnailUrl = null)
        assertEquals(
            listOf(BearerPhotoUploadEvent.Progress(0.4), BearerPhotoUploadEvent.Progress(1.0), BearerPhotoUploadEvent.Completed(photo)),
            events,
        )
        assertArrayEquals(JPEG_SOI, repository.uploaded?.copyOf(3))
    }

    @Test
    fun rejectsSmallPhoto() = runTest {
        val repository = PhotoRepository()

        val error = failure { useCase(repository).uploadPhoto("flg_1", png(200, 200)).toList() }

        assertEquals(BearerError.PhotoTooSmall(), error)
        assertNull(repository.uploaded)
    }

    @Test
    fun tooSmall() {
        assertEquals(BearerError.PhotoTooSmall(), encodeFailure(png(255, 600)))
    }

    @Test
    fun downscales() {
        val jpeg = BearerPhotoEncoder().encode(png(3000, 1000))

        assertTrue(jpeg.size <= BearerPhotoEncoder.MAX_BYTES)
        assertArrayEquals(JPEG_SOI, jpeg.copyOf(3))
        val (width, height) = pixelSize(jpeg)
        assertEquals(2048, width)
        assertTrue(height >= 256)
    }

    @Test
    fun notImage() {
        assertEquals(BearerError.UnsupportedPhoto(), encodeFailure("text".encodeToByteArray()))
    }

    @Test
    fun stripsExifAndLocation() {
        val source = jpegWithExif(halves(400, 300), ExifInterface.ORIENTATION_NORMAL)
        assertTrue("원본에는 EXIF가 있어야 한다", hasExifSegment(source))
        assertEquals("TestCam", ExifInterface(ByteArrayInputStream(source)).getAttribute(ExifInterface.TAG_MAKE))

        for (encoder in ENCODERS) {
            val jpeg = encoder.encode(source)

            assertTrue("결과 JPEG에 APP1(Exif) 세그먼트가 없어야 한다", !hasExifSegment(jpeg))
            val exif = ExifInterface(ByteArrayInputStream(jpeg))
            assertTrue("위치가 없어야 한다", !exif.getLatLong(FloatArray(2)))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
        }
    }

    @Test
    fun appliesExifOrientation() {
        // 가로 400×300(왼쪽 빨강 · 오른쪽 파랑)을 90도 회전으로 표시하라는 EXIF → 세로 300×400, 위 빨강 · 아래 파랑
        val source = jpegWithExif(halves(400, 300), ExifInterface.ORIENTATION_ROTATE_90)

        for (encoder in ENCODERS) {
            val bitmap = decode(encoder.encode(source))

            assertEquals(300 to 400, bitmap.width to bitmap.height)
            assertTrue(isRed(bitmap.getPixel(150, 50)))
            assertTrue(isBlue(bitmap.getPixel(150, 350)))
        }
    }

    @Test
    fun sizePlan() {
        assertEquals(BearerPhotoEncoder.SizePlan(2048, 683, 1), BearerPhotoEncoder.SizePlan.of(3000, 1000))
        // 긴 변을 2048로 줄이면 짧은 변이 256 밑이라 긴 변을 덜 줄인다.
        assertEquals(BearerPhotoEncoder.SizePlan(2560, 256, 2), BearerPhotoEncoder.SizePlan.of(5120, 512))
        assertEquals(BearerPhotoEncoder.SizePlan(300, 300, 1), BearerPhotoEncoder.SizePlan.of(300, 300))
        assertEquals(BearerPhotoEncoder.SizePlan(2048, 1536, 4), BearerPhotoEncoder.SizePlan.of(8192, 6144))
    }

    private fun useCase(repository: BearerRepository) = BearerUseCaseImpl(repository, StubPackSyncService(null))

    private fun encodeFailure(data: ByteArray): Throwable? = try {
        BearerPhotoEncoder().encode(data)
        null
    } catch (e: Exception) {
        e
    }

    /** 진행률을 0.4 · 1.2로 알리고 빈 주소를 돌려준다. iOS `PhotoRepository`. */
    private class PhotoRepository : BearerRepository by ClaimRecordingRepository() {
        var uploaded: ByteArray? = null

        override suspend fun uploadPhoto(
            flagId: String,
            jpeg: ByteArray,
            onProgress: (Double) -> Unit,
        ): BearerFlagPhoto {
            uploaded = jpeg
            onProgress(0.4)
            onProgress(1.2)
            return BearerFlagPhoto(photoUrl = null, thumbnailUrl = null)
        }
    }

    private companion object {
        val JPEG_SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        /** 기본(ImageDecoder, API 28+) 경로와 API 26-27 경로 */
        val ENCODERS = listOf(BearerPhotoEncoder(useImageDecoder = true), BearerPhotoEncoder(useImageDecoder = false))

        fun png(width: Int, height: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(204, 51, 102)) }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }

        /** 왼쪽 절반 빨강 · 오른쪽 절반 파랑 */
        fun halves(width: Int, height: Int): Bitmap {
            val pixels = IntArray(width * height) { index -> if (index % width < width / 2) Color.RED else Color.BLUE }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        /** 위치 · 기기 · 시각 · 방향 EXIF를 넣은 JPEG */
        fun jpegWithExif(bitmap: Bitmap, orientation: Int): ByteArray {
            val file = File.createTempFile("photo", ".jpg")
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                ExifInterface(file.path).apply {
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,33/1,0/1")
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "126/1,58/1,0/1")
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                    setAttribute(ExifInterface.TAG_MAKE, "TestCam")
                    setAttribute(ExifInterface.TAG_DATETIME, "2026:11:24 12:00:00")
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    saveAttributes()
                }
                return file.readBytes()
            } finally {
                file.delete()
            }
        }

        /** JPEG 세그먼트를 SOS 전까지 훑어 `Exif\0\0`로 시작하는 APP1이 있는지 본다. */
        fun hasExifSegment(jpeg: ByteArray): Boolean {
            var offset = 2
            while (offset + 4 <= jpeg.size && jpeg[offset] == 0xFF.toByte()) {
                val marker = jpeg[offset + 1].toInt() and 0xFF
                if (marker == 0xDA) return false
                val length = ((jpeg[offset + 2].toInt() and 0xFF) shl 8) or (jpeg[offset + 3].toInt() and 0xFF)
                val isExif = marker == 0xE1 && String(jpeg, offset + 4, minOf(4, jpeg.size - offset - 4), Charsets.ISO_8859_1) == "Exif"
                if (isExif) return true
                offset += 2 + length
            }
            return false
        }

        fun decode(jpeg: ByteArray): Bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

        fun pixelSize(jpeg: ByteArray): Pair<Int, Int> {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            return options.outWidth to options.outHeight
        }

        fun isRed(color: Int) = Color.red(color) > 200 && Color.blue(color) < 60

        fun isBlue(color: Int) = Color.blue(color) > 200 && Color.red(color) < 60
    }
}
