package com.bal.reminders.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.ui.theme.Space

/**
 * The licence texts the project is required to distribute.
 *
 * This is not a credits page. SIL OFL 1.1 and Apache 2.0 both require the
 * licence to travel with the work, so the two texts ship in the APK's assets
 * and are shown here in full. Nothing else belongs on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licenses = remember {
        listOf(
            LicenseEntry(
                nameRes = R.string.licenses_tajawal,
                licenseRes = R.string.licenses_tajawal_license,
                text = context.readAsset("licenses/Tajawal-OFL.txt"),
            ),
            LicenseEntry(
                nameRes = R.string.licenses_androidx,
                licenseRes = R.string.licenses_androidx_license,
                text = context.readAsset("licenses/Apache-2.0.txt"),
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            licenses.forEach { entry ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Text(
                            stringResource(entry.nameRes),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(entry.licenseRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        // The licence text is English and left-to-right; letting
                        // the RTL layout reflow it would corrupt the very thing
                        // the licence requires to be reproduced intact.
                        Text(
                            entry.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDirection = TextDirection.Ltr,
                            ),
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

private data class LicenseEntry(
    val nameRes: Int,
    val licenseRes: Int,
    val text: String,
)

private fun android.content.Context.readAsset(path: String): String =
    runCatching { assets.open(path).bufferedReader().use { it.readText() }.trim() }
        .getOrDefault("")
