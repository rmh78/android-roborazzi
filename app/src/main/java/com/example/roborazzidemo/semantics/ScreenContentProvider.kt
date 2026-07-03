package com.example.roborazzidemo.semantics

object ScreenContentProvider {
    fun dumpScreenJson(): String = ScreenContentRegistry.toJson()
}