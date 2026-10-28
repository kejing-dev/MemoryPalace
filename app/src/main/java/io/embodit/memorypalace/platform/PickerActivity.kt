package io.embodit.memorypalace.platform

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File

/**
 * Transparent helper behind "Choose from files…" (design `import.where`): a Stage has no Activity of
 * its own to receive a result, so this one opens the system picker (`ACTION_OPEN_DOCUMENT`), copies the
 * chosen file into `files/import/` and finishes. The import list picks it up from there. Whether the
 * picker exists on PICO OS 6 is a device check (design `verification.deviceOnly`); without one the
 * activity finishes at once and the `import/` folder stays the way in.
 */
class PickerActivity : Activity() {
    /** v0.3: picking an image or a model for a locus (design `userContent.fileAccess`), not an import file. */
    private val media: String?
        get() = intent?.getStringExtra(EXTRA_MEDIA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val mimes =
            when (media) {
                MEDIA_IMAGE -> arrayOf("image/png", "image/jpeg", "image/webp")
                MEDIA_MODEL -> arrayOf("model/gltf-binary", "model/vnd.usdz+zip", "application/octet-stream")
                else -> arrayOf("text/*", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")
            }
        val pick =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (media == MEDIA_IMAGE) "image/*" else "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, mimes)
            }
        val ok = runCatching { startActivityForResult(pick, REQUEST) }.isSuccess
        PalaceLog.i("IMPORT picker media=$media started=$ok")
        if (!ok) {
            if (media != null) deliver(null, "none") else result = "none"
            finish()
        }
    }

    @Deprecated("Activity result API of the platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (media != null) {
            // the media item: copied into the cache first, the ViewModel scales / hashes it into the palace
            var picked: File? = null
            var name = ""
            if (requestCode == REQUEST && resultCode == RESULT_OK && uri != null) {
                runCatching {
                    name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "picked"
                    val out = File(cacheDir, "picked-" + System.nanoTime())
                    contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    picked = out
                    PalaceLog.i("MEDIA picked $name bytes=${out.length()}")
                }.onFailure { PalaceLog.e("MEDIA pick failed: $it", it) }
            }
            deliver(picked, name)
            finish()
            return
        }
        if (requestCode == REQUEST && resultCode == RESULT_OK && uri != null) {
            runCatching {
                val name =
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?: "import.txt"
                val dir = importDir(this)
                val out = File(dir, name.replace('/', '_'))
                contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                result = out.name
                PalaceLog.i("IMPORT picked ${out.name} bytes=${out.length()}")
            }.onFailure { PalaceLog.e("IMPORT pick failed: $it", it) }
        }
        finish()
    }

    companion object {
        private const val REQUEST = 41
        const val EXTRA_MEDIA = "media"
        const val MEDIA_IMAGE = "image"
        const val MEDIA_MODEL = "model"

        /** Where a media pick goes (the ViewModel, main thread); null file: nothing picked, name "none": no picker. */
        @Volatile var mediaSink: ((File?, String) -> Unit)? = null

        private fun deliver(file: File?, name: String) {
            val sink = mediaSink ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { sink(file, name) }
        }

        /** The file the last pick copied in (null: nothing yet, "none": no picker on this system). */
        @Volatile var result: String? = null

        fun importDir(context: android.content.Context): File = (context.getExternalFilesDir("import") ?: File(context.filesDir, "import")).also { it.mkdirs() }
    }
}
