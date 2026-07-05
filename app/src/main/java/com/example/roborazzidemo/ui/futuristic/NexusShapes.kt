package com.example.roborazzidemo.ui.futuristic

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** LCARS horizontal bar — large round cap on the left. */
fun LcarsBarShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = 28.dp,
    bottomStart = 28.dp,
    topEnd = 4.dp,
    bottomEnd = 4.dp,
)

/** LCARS content panel — asymmetric rounded corners. */
fun LcarsPanelShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 48.dp,
    bottomStart = 24.dp,
    bottomEnd = 4.dp,
)

val LcarsPillShape = RoundedCornerShape(percent = 50)

/** @deprecated Use [LcarsPanelShape]. */
typealias ChamferedPanelShape = RoundedCornerShape