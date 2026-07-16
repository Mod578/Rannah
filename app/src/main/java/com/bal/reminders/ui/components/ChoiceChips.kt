package com.bal.reminders.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One choice out of a few, as chips that wrap.
 *
 * This replaces the segmented rows that were stacked four and five deep on a
 * screen. Segments divide the width evenly whatever the words need, so «هجري
 * وميلادي» was rendering with its text sliced off at the edge of its own
 * segment: an option the user could not read. Chips size to their label and
 * wrap to the next line, so the same control survives long Arabic labels and
 * the largest font scales.
 *
 * Using the same chip everywhere also means "selected" looks like one thing
 * across رَنّة instead of a different thing per screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
