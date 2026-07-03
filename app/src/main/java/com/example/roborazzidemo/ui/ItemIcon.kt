package com.example.roborazzidemo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.roborazzidemo.model.ItemIconType

fun ItemIconType.asImageVector(): ImageVector = when (this) {
    ItemIconType.Star -> Icons.Filled.Star
    ItemIconType.Folder -> Icons.AutoMirrored.Filled.List
    ItemIconType.Person -> Icons.Filled.Person
    ItemIconType.Settings -> Icons.Filled.Settings
    ItemIconType.ShoppingCart -> Icons.Filled.ShoppingCart
    ItemIconType.Email -> Icons.Filled.Email
    ItemIconType.Photo -> Icons.Filled.Face
    ItemIconType.MusicNote -> Icons.Filled.PlayArrow
    ItemIconType.LocalCafe -> Icons.Filled.Favorite
    ItemIconType.Flight -> Icons.Filled.LocationOn
}