package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class FindroidPlaylist(
    val id: UUID,
    val name: String,
    val images: FindroidImages,
)

fun BaseItemDto.toFindroidPlaylist(repository: JellyfinRepository): FindroidPlaylist {
    return FindroidPlaylist(
        id = id,
        name = name.orEmpty(),
        images = toFindroidImages(repository),
    )
}
