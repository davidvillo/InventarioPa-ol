package com.example.stockpaniol

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal fun cardTitleColor(background: Color): Color = lerp(background, Color.Black, 0.06f)
