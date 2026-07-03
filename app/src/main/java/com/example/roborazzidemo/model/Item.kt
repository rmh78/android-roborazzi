package com.example.roborazzidemo.model

import androidx.annotation.StringRes
import com.example.roborazzidemo.R

data class Item(
    val id: Int,
    val title: String,
    @StringRes val descriptionRes: Int,
    val iconType: ItemIconType,
    val iconSizeDp: Int = 24,
)

enum class ItemIconType(@StringRes val contentDescriptionRes: Int) {
    Star(R.string.icon_desc_star),
    Folder(R.string.icon_desc_folder),
    Person(R.string.icon_desc_person),
    Settings(R.string.icon_desc_settings),
    ShoppingCart(R.string.icon_desc_shopping_cart),
    Email(R.string.icon_desc_email),
    Photo(R.string.icon_desc_image),
    MusicNote(R.string.icon_desc_music),
    LocalCafe(R.string.icon_desc_restaurant),
    Flight(R.string.icon_desc_place),
}

private val descriptionResources = listOf(
    R.string.item_desc_short,
    R.string.item_desc_medium,
    R.string.item_desc_long,
    R.string.item_desc_brief,
    R.string.item_desc_medium_alt,
)

private val cachedSampleItems: List<Item> by lazy {
    val iconTypes = ItemIconType.entries
    val iconSizes = listOf(24, 32, 40, 48)

    (1..25).map { index ->
        Item(
            id = index,
            title = "Item $index",
            descriptionRes = descriptionResources[(index - 1) % descriptionResources.size],
            iconType = iconTypes[(index - 1) % iconTypes.size],
            iconSizeDp = iconSizes[(index - 1) % iconSizes.size],
        )
    }
}

fun sampleItems(): List<Item> = cachedSampleItems

fun findItemById(id: Int): Item? = cachedSampleItems.find { it.id == id }