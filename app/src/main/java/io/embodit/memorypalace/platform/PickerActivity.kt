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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val pick =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
            }
        val ok = runCatching { startActivityForResult(pick, REQUEST) }.isSuccess
        PalaceLog.i("IMPORT picker started=$ok")
        if (!ok) {
            result = "none"
            finish()
        }
    }

    @Deprecated("Activity result API of the platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
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

        /** The file the last pick copied in (null: nothing yet, "none": no picker on this system). */
        @Volatile var result: String? = null

        fun importDir(context: android.content.Context): File = (context.getExternalFilesDir("import") ?: File(context.filesDir, "import")).also { it.mkdirs() }
    }
}
