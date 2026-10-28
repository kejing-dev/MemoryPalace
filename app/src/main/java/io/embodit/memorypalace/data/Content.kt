package io.embodit.memorypalace.data

import io.embodit.memorypalace.domain.MediaError
import java.io.File

/** The content library's bundled JSON (`assets/packs/`; a fake in tests). */
interface PackSource {
    fun index(): String?

    fun volume(file: String): String?
}

sealed interface MediaOutcome {
    /** [file] is relative to the files dir; [scaled]: an image was shrunk to 1024 px. */
    data class Ok(val file: String, val scaled: Boolean) : MediaOutcome

    data class Failed(val error: MediaError) : MediaOutcome
}

/** Copies picked images / models into the palace's media folder (design `userContent.storage`). */
interface MediaGateway {
    suspend fun image(src: File, name: String, palaceId: String): MediaOutcome

    suspend fun model(src: File, name: String, palaceId: String): MediaOutcome

    fun absolute(file: String): String

    fun delete(file: String)
}
