package ai.drfx.maximus.matrixai.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageInputPipelineTest {
    @Test fun largePngIsDownsampledAndNormalizedToJpeg() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "matrix-vision-pipeline.png")
        val bitmap = Bitmap.createBitmap(2304, 1152, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val result = ImageInputPipeline.prepare(context, Uri.fromFile(file), "diagram.png")
            assertEquals("diagram.jpg", result.name)
            assertTrue(result.width <= 2048 && result.height <= 2048)
            assertTrue(result.jpeg.size < 4 * 1024 * 1024)
            val decoded = BitmapFactory.decodeByteArray(result.jpeg, 0, result.jpeg.size)
            assertNotNull(decoded)
            decoded.recycle()
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
