package com.ebookreader.simplebook.domain.model

enum class CollectionIcon { HEART, BOOKMARK, FLOWER, LEAF, DIAMOND, MOON, SPROUT, WAVE }

enum class ReaderTheme(
    val key: String,
    val backgroundColor: Long,
    val textColor: Long,
    val isDark: Boolean,
    val accentColor: Long,
    val collectionIcon: CollectionIcon,
) {
    DEFAULT_WHITE(
        key = "default_white",
        backgroundColor = 0xFFFFFFFF,
        textColor = 0xFF1C1B1F,
        isDark = false,
        accentColor = 0xFF6750A4,
        collectionIcon = CollectionIcon.HEART,
    ),
    SEPIA(
        key = "sepia",
        backgroundColor = 0xFFF5F0E1,
        textColor = 0xFF3E3B36,
        isDark = false,
        accentColor = 0xFF8B7355,
        collectionIcon = CollectionIcon.BOOKMARK,
    ),
    CHERRY_PINK(
        key = "cherry_pink",
        backgroundColor = 0xFFFFF8F9,
        textColor = 0xFF2C2C2C,
        isDark = false,
        accentColor = 0xFFFFB7C5,
        collectionIcon = CollectionIcon.FLOWER,
    ),
    MINT_GREEN(
        key = "mint_green",
        backgroundColor = 0xFFF4F9F4,
        textColor = 0xFF232D26,
        isDark = false,
        accentColor = 0xFF8CBE8F,
        collectionIcon = CollectionIcon.LEAF,
    ),
    SAPPHIRE_BLUE(
        key = "sapphire_blue",
        backgroundColor = 0xFFF4F8FA,
        textColor = 0xFF202731,
        isDark = false,
        accentColor = 0xFF89B8E8,
        collectionIcon = CollectionIcon.DIAMOND,
    ),
    NIGHT_SAKURA(
        key = "night_sakura",
        backgroundColor = 0xFF1A1520,
        textColor = 0xFFE8C5D0,
        isDark = true,
        accentColor = 0xFFFFB7C5,
        collectionIcon = CollectionIcon.MOON,
    ),
    DARK_GREEN(
        key = "dark_green",
        backgroundColor = 0xFF121A14,
        textColor = 0xFFB8D4BA,
        isDark = true,
        accentColor = 0xFFA1D6A4,
        collectionIcon = CollectionIcon.SPROUT,
    ),
    DEEP_SEA(
        key = "deep_sea",
        backgroundColor = 0xFF0F141C,
        textColor = 0xFFA8C8E8,
        isDark = true,
        accentColor = 0xFFA4C9FF,
        collectionIcon = CollectionIcon.WAVE,
    );

    companion object {
        fun fromKey(key: String?): ReaderTheme =
            entries.find { it.key == key } ?: DEFAULT_WHITE

        fun fromLegacyBackgroundColor(color: Long): ReaderTheme = when (color) {
            0xFFF5F0E1L -> SEPIA
            else -> DEFAULT_WHITE
        }
    }
}
