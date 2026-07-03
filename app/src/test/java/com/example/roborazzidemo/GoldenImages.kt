package com.example.roborazzidemo

object GoldenImages {
    const val HOME_DEFAULT = "HomeScreen_default"
    const val HOME_DARK = "HomeScreen_dark"

    fun homeScreenResolution(slug: String, theme: String) = "HomeScreen_${slug}_$theme"
    const val ITEM_LIST_SCROLL_TOP = "ItemListScreen_scroll_top"
    const val ITEM_LIST_SCROLL_MIDDLE = "ItemListScreen_scroll_middle"
    const val ITEM_LIST_SCROLL_BOTTOM = "ItemListScreen_scroll_bottom"
    const val ITEM_DETAIL_SAMPLE = "ItemDetailScreen_sample"
    const val ITEM_DETAIL_LONG = "ItemDetailScreen_long"
    const val ITEM_NOT_FOUND = "ItemNotFoundScreen_default"
    const val NAV_BROWSE_ITEMS_LIST = "AppNavHost_browse_items_list"
    const val NAV_BROWSE_ITEMS_DETAIL = "AppNavHost_browse_items_detail"
    const val NAV_SAMPLE_DETAIL = "AppNavHost_sample_detail"
    const val NAV_ITEM_NOT_FOUND = "AppNavHost_item_not_found"
}