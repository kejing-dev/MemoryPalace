package io.embodit.memorypalace.ui.palace

import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Spot
import io.embodit.memorypalace.domain.Vec3

/** Everything that can happen: panel controls, scene taps and the per-frame rules of the scene. */
sealed interface PalaceEvent {
    // ---- scene
    data object SceneReady : PalaceEvent

    /** World tracking moved an anchor (coordinate recalibration, `AnchorUpdate.Event.UPDATED`). */
    data class AnchorMoved(val anchorId: String, val position: Vec3, val rotation: Quat4) : PalaceEvent

    /** The reticle moved onto a spot that can / cannot take a locus (null block: it can). */
    data class AimChanged(val block: BlockReason?) : PalaceEvent

    /** Pinch in the room while placing (v0.2.2: anywhere, the locus floats along the pinch direction). */
    data class PlaceTapped(val spot: Spot?, val block: BlockReason?) : PalaceEvent

    /**
     * Pinch / trigger on a locus (plaque, dot or its label), at any distance (v0.3 design `interactionModel`);
     * [distance] is only logged.
     */
    data class LocusTapped(val nodeId: String, val distance: Float) : PalaceEvent

    data class CellTapped(val nodeId: String, val distance: Float) : PalaceEvent

    data class TitleTapped(val distance: Float) : PalaceEvent

    /** v0.3: a 3D memory object was pinched (placing: it becomes the one the stick turns). */
    data class ModelTapped(val nodeId: String) : PalaceEvent

    /** v0.3: the stick turned the selected model; sent when the stick comes back to rest. */
    data class ModelTurned(val nodeId: String, val yawDeg: Float) : PalaceEvent

    /** v0.3 S37: the picked model loaded (or not) and was measured. */
    data class ModelLoaded(val file: String, val ok: Boolean, val heavy: Boolean) : PalaceEvent

    // ---- pocket panel
    data object ChipClicked : PalaceEvent

    data object SettingsClicked : PalaceEvent

    data object StartPlacing : PalaceEvent

    data object StartReview : PalaceEvent

    data object StartRecall : PalaceEvent

    data object EndSession : PalaceEvent

    data object UndoPlace : PalaceEvent

    data object DonePlacing : PalaceEvent

    data object UpLevel : PalaceEvent

    data class Rate(val q: Int) : PalaceEvent

    data object UndoRate : PalaceEvent

    data class Judge(val right: Boolean) : PalaceEvent

    data object SummaryOk : PalaceEvent

    data object EnterAnyway : PalaceEvent

    data object GrantPermission : PalaceEvent

    data object RetryLost : PalaceEvent

    data object ReplaceLost : PalaceEvent

    data object DeleteLocus : PalaceEvent

    data object ConfirmYes : PalaceEvent

    data object ConfirmNo : PalaceEvent

    // ---- popups
    data object PopupClosed : PalaceEvent

    data class PalacePicked(val id: String) : PalaceEvent

    data object ImportClicked : PalaceEvent

    data object ImportBack : PalaceEvent

    data class ImportFile(val name: String) : PalaceEvent

    data object PickFromFiles : PalaceEvent

    data object ImportErrorOk : PalaceEvent

    data class LanguagePicked(val pref: LanguagePref) : PalaceEvent

    data object Relocate : PalaceEvent

    data object TearDown : PalaceEvent

    // ---- v0.2 content sources (S27 - S31)
    data object NewPalace : PalaceEvent

    /** ‹ on a popup page: one page up (preview -> search -> new palace -> library). */
    data object PopupBack : PalaceEvent

    data object SourcePoetry : PalaceEvent

    data object SourceWiki : PalaceEvent

    data object SourceFile : PalaceEvent

    data class BuildPack(val id: String) : PalaceEvent

    /** Typed into the search field (searched 300 ms after the last key). */
    data class WikiQuery(val text: String) : PalaceEvent

    /** Search now (the keyboard's search key, a "try" chip). */
    data class WikiSearch(val text: String) : PalaceEvent

    data class WikiLang(val lang: String) : PalaceEvent

    data class WikiPick(val title: String, val description: String) : PalaceEvent

    data class WikiToggle(val index: Int) : PalaceEvent

    data object WikiBuild : PalaceEvent

    data object WikiRetry : PalaceEvent

    data object GoPoetry : PalaceEvent

    // ---- v0.3 content library (S32), blank palace (S33), add an item (S34 - S38)
    data object SourceLibrary : PalaceEvent

    data object SourceBlank : PalaceEvent

    data class LibraryCategory(val category: String) : PalaceEvent

    data class LibraryExpand(val collectionId: String) : PalaceEvent

    data class AddVolume(val volumeId: String) : PalaceEvent

    data class AddGenerated(val collectionId: String) : PalaceEvent

    /** S33 「开始布置」 with the name typed into [PalaceViewModel.nameInput]. */
    data object BlankCreate : PalaceEvent

    /** The 「＋」 cell of an open locus while placing. */
    data object AddItem : PalaceEvent

    data object AddText : PalaceEvent

    data object AddImage : PalaceEvent

    data object AddModel : PalaceEvent

    data object AddGroup : PalaceEvent

    data class AddBeside(val beside: Boolean) : PalaceEvent

    /** Done on S35 / S36 / S37 / the sub-locus or rename name. */
    data object AddDone : PalaceEvent

    /** Edit bar: 「改名」 / 「放一个记忆物」. */
    data object RenameLocus : PalaceEvent

    data object PlaceObject : PalaceEvent

    /** The system picker gave a file back (copied to [file]; [name] is its own name); null: nothing picked. */
    data class MediaPicked(val file: java.io.File?, val name: String) : PalaceEvent
}
