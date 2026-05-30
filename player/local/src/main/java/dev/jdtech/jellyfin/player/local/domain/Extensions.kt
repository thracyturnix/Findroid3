package dev.jdtech.jellyfin.player.local.domain

import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Tracks
import java.util.Locale

fun List<Tracks.Group>.getTrackNames(): Array<String> {
    return this.mapIndexed { index, group ->
            val nameParts: MutableList<String?> = mutableListOf()
            val format = group.mediaTrackGroup.getFormat(0)
            nameParts.run {
                add(format.label)
                add(
                    format.language?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            Locale.of(it.split("-").last()).displayLanguage
                        } else {
                            @Suppress("DEPRECATION") Locale(it.split("-").last()).displayLanguage
                        }
                    }
                )
                add(format.codecs)
                if ((format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0) {
                    add("Default")
                }
                if ((format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) {
                    add("Forced")
                }
                val sdhRoleFlags =
                    C.ROLE_FLAG_CAPTION or
                        C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or
                        C.ROLE_FLAG_TRANSCRIBES_DIALOG
                if ((format.roleFlags and sdhRoleFlags) != 0) {
                    add("SDH")
                }
                filterNotNull().joinToString(separator = " - ").ifBlank { "Track ${index + 1}" }
            }
        }
        .toTypedArray()
}
