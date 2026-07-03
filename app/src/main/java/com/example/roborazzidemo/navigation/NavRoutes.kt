package com.example.roborazzidemo.navigation

object NavRoutes {
    const val Home = "home"
    const val Items = "items"
    const val Detail = "detail/{itemId}"

    fun detail(itemId: Int): String = "detail/$itemId"
}