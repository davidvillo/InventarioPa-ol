package com.example.stockpaniol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Share one column layout between the fixed header and all rows, at every zoom level. */
@Composable
internal fun CompleteTable(
    columnWidths: List<Int>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    header: @Composable (List<Dp>) -> Unit,
    content: LazyListScope.(List<Dp>) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minimumWidth = columnWidths.sum().dp
        val tableWidth = maxOf(maxWidth, minimumWidth)
        val widths = columnWidths.map { tableWidth * (it.toFloat() / columnWidths.sum()) }
        Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth).fillMaxHeight()) {
            header(widths)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = contentPadding) { content(widths) }
        }
    }
}

@Composable
internal fun CompleteTableRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), content = content)
}

@Composable
internal fun CompleteTableCell(
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.width(width).fillMaxHeight().heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center, content = content)
}

@Composable
internal fun CompleteTableText(
    text: String,
    width: Dp,
    modifier: Modifier = Modifier,
    header: Boolean = false,
    bold: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    CompleteTableCell(width, modifier.then(if (onClick != null) Modifier.clickable(
        onClickLabel = "Ordenar por $text", onClick = onClick) else Modifier)) {
        Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = if (header) Color.White else Color.Unspecified,
            fontWeight = if (header || bold) FontWeight.Bold else FontWeight.Normal)
    }
}
