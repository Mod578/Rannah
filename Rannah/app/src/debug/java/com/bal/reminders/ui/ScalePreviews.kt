package com.bal.reminders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bal.reminders.ui.components.ChecklistRow
import com.bal.reminders.ui.components.ClosedRow
import com.bal.reminders.ui.components.RowTone
import com.bal.reminders.ui.components.SlideToConfirm
import com.bal.reminders.ui.theme.BalTheme

/**
 * The layouts that are hardest to get right, rendered at the font scales and
 * widths that break them.
 *
 * These exist because the two clipping defects رَنّة shipped in 1.0 were both
 * invisible at 100%: «اسحب للتأكيد» ran out of a fixed 76dp track at 200%, on
 * the one screen where an ambiguous confirmation is unacceptable, and the
 * details action pair truncated «تخطي اليوم» inside half a phone's width. Both
 * are now checkable without a device, and any regression shows up in the
 * preview pane rather than on someone's phone at 6am.
 *
 * Debug-only: nothing here ships in the release APK.
 */
private const val LONG_TITLE = "مراجعة الطبيب في المستشفى التخصصي مع تقرير التحاليل"

@Preview(name = "row · 100%", locale = "ar", showBackground = true, widthDp = 360)
@Preview(name = "row · 150%", locale = "ar", showBackground = true, widthDp = 360, fontScale = 1.5f)
@Preview(name = "row · 200%", locale = "ar", showBackground = true, widthDp = 360, fontScale = 2.0f)
@Preview(name = "row · 200% narrow", locale = "ar", showBackground = true, widthDp = 320, fontScale = 2.0f)
@Composable
private fun ChecklistRowScales() {
    BalTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChecklistRow(
                    title = LONG_TITLE,
                    meta = "٩:٠٠ صباحًا · ينتظر تأكيدك",
                    kindLabel = "يومي",
                    onClick = {},
                    tone = RowTone.Waiting,
                    onComplete = {},
                    onSkip = {},
                )
                ChecklistRow(
                    title = "الدواء",
                    meta = "كان موعده ٢٠ يونيو، ٩:٠٠ صباحًا · لم يُغلق بعد",
                    kindLabel = "مرة واحدة",
                    onClick = {},
                    tone = RowTone.Overdue,
                    onComplete = {},
                )
                ClosedRow(
                    title = LONG_TITLE,
                    meta = "مكتمل · القادمة غدًا، ٩:٠٠ صباحًا",
                    completed = true,
                    onUndo = {},
                )
            }
        }
    }
}

@Preview(name = "slide · 100%", locale = "ar", showBackground = true, widthDp = 360)
@Preview(name = "slide · 150%", locale = "ar", showBackground = true, widthDp = 360, fontScale = 1.5f)
@Preview(name = "slide · 200%", locale = "ar", showBackground = true, widthDp = 360, fontScale = 2.0f)
@Preview(name = "slide · 200% narrow", locale = "ar", showBackground = true, widthDp = 320, fontScale = 2.0f)
@Composable
private fun SlideToConfirmScales() {
    BalTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                SlideToConfirm(
                    text = "اسحب للتأكيد",
                    hint = "اسحب حتى النهاية",
                    onConfirm = {},
                )
            }
        }
    }
}
