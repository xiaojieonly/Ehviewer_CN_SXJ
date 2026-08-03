package com.hippo.anotherviewer.web.entity

import jakarta.persistence.*

/**
 * Local favorites table.
 *
 * `favoriteSlot` (declared on [GalleryInfoBase]) follows the Android contract:
 * -2 = not favorited, -1 = default folder, 0-9 = custom folder slots. The web
 * layer must never write a category bitmask (up to 512) into it; only
 * `FavoriteService.addFavorite` writes it, clamped to [-2, 9]. Legacy rows
 * carrying out-of-range values (e.g. 512, pre-N-5) may exist until migrated.
 */
@Entity
@Table(name = "local_favorite_info")
class LocalFavoriteInfoEntity : GalleryInfoBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false)
    var time: Long = 0

    @Column(nullable = false)
    var lastModified: Long = 0

    @Column(nullable = false)
    var deleted: Boolean = false

    @Column(length = 256)
    var username: String? = null
}
