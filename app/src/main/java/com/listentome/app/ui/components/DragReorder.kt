package com.listentome.app.ui.components

import androidx.compose.foundation.lazy.LazyListState

/**
 * Checks whether the dragged item (at [draggedIndex], visually offset by [dragOffsetY] pixels from
 * its laid-out position) has been dragged past the center of an adjacent item, using each item's
 * real measured position and size from [listState] rather than assuming a uniform row height. If
 * so, swaps them and returns the updated items/index/offset; otherwise returns null. Meant to be
 * called on every drag/auto-scroll update, so a fast drag cascades across multiple rows over
 * successive calls rather than needing to resolve every swap in one pass.
 */
fun <T> performReorderSwap(
    listState: LazyListState,
    items: List<T>,
    draggedIndex: Int,
    dragOffsetY: Float
): Triple<List<T>, Int, Float>? {
    val draggedInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex } ?: return null
    val draggedCenter = draggedInfo.offset + dragOffsetY + draggedInfo.size / 2f

    if (dragOffsetY < 0 && draggedIndex > 0) {
        val prevInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex - 1 }
        if (prevInfo != null && draggedCenter < prevInfo.offset + prevInfo.size / 2f) {
            val newOffset = dragOffsetY - (prevInfo.offset - draggedInfo.offset)
            val newItems = items.toMutableList().apply { add(draggedIndex - 1, removeAt(draggedIndex)) }
            return Triple(newItems, draggedIndex - 1, newOffset)
        }
    } else if (dragOffsetY > 0 && draggedIndex < items.lastIndex) {
        val nextInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex + 1 }
        if (nextInfo != null && draggedCenter > nextInfo.offset + nextInfo.size / 2f) {
            val newOffset = dragOffsetY - (nextInfo.offset - draggedInfo.offset)
            val newItems = items.toMutableList().apply { add(draggedIndex + 1, removeAt(draggedIndex)) }
            return Triple(newItems, draggedIndex + 1, newOffset)
        }
    }
    return null
}
