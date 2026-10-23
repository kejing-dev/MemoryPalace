package io.embodit.memorypalace.ui.palace

import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.Spot

/** Everything that can happen: panel controls, scene taps and the per-frame rules of the scene. */
sealed interface PalaceEvent {
    // ---- scene
    data object SceneReady : PalaceEvent

    data class SurfacesChanged(val usable: Int, val error: String?) : PalaceEvent

    /** The reticle moved onto a spot that can / cannot take a locus (null block: it can). */
    data class AimChanged(val block: BlockReason?) : PalaceEvent

    /** Pinch on a surface while placing. */
    data class PlaceTapped(val spot: Spot?, val block: BlockReason?) : PalaceEvent

    /** Pinch on a locus (dot or plaque); [distance] is horizontal, head to locus. */
    data class LocusTapped(val nodeId: String, val distance: Float) : PalaceEvent

    /** The wake rule picked another open locus (null: none). */
    data class LocusWoke(val nodeId: String?) : PalaceEvent

    /** Recall: walked up to a locus that is not next (null: away again). */
    data class NearOther(val nodeId: String?) : PalaceEvent

    data class CellTapped(val nodeId: String, val distance: Float) : PalaceEvent

    data class TitleTapped(val distance: Float) : PalaceEvent

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
}
