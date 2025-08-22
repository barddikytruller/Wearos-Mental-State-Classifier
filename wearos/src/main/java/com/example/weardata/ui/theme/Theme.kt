package com.example.weardata.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearDataTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(

        content = content
    )
}