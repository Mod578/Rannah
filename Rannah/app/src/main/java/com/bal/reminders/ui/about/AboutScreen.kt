package com.bal.reminders.ui.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bal.reminders.R
import com.bal.reminders.format.BalFormats
import com.bal.reminders.ui.components.AppMark
import com.bal.reminders.ui.components.MadeInSaudi
import com.bal.reminders.ui.theme.Space

/**
 * «عن رَنّة»: the mark, the version, who made it, and the four links that belong
 * to the project rather than to the app's behaviour. Privacy lives in settings,
 * beside the other things a person goes looking for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val linkFailed = stringResource(R.string.about_link_failed)
    val versionName = remember {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName.orEmpty()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(top = Space.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                AppMark(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(84.dp))
                Spacer(Modifier.height(Space.sm))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        R.string.about_version,
                        BalFormats.ltr(BalFormats.arabicDigits(versionName)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InfoCard {
                Text(
                    stringResource(R.string.about_developer_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_developer_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Contact and project links. Each opens through ACTION_VIEW and
            // fails to a message rather than a crash when the device has no
            // application that can handle it, which is the ordinary case for a
            // mailto: on a tablet with no mail account.
            AboutRow(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.about_linkedin),
                description = stringResource(R.string.about_linkedin_description),
                onClick = { context.openUri(LINKEDIN_URL, linkFailed) },
            )
            AboutRow(
                icon = Icons.Rounded.MailOutline,
                label = stringResource(R.string.about_email),
                description = stringResource(R.string.about_email_description),
                onClick = { context.openUri(EMAIL_URI, linkFailed) },
            )
            AboutRow(
                icon = Icons.Rounded.Code,
                label = stringResource(R.string.about_github),
                description = stringResource(R.string.about_github_description),
                onClick = { context.openUri(GITHUB_URL, linkFailed) },
            )

            AboutRow(
                icon = Icons.Rounded.Description,
                label = stringResource(R.string.licenses_title),
                description = stringResource(R.string.licenses_title),
                onClick = onOpenLicenses,
            )

            MadeInSaudi(Modifier.padding(top = Space.sm))

            Spacer(Modifier.height(Space.lg))
        }
    }
}

/**
 * One tappable line in «عن رَنّة». The whole surface is the target and it is at
 * least 64dp tall, so it clears the 48dp minimum comfortably; the chevron is
 * auto-mirrored, so it points the way the next screen arrives from in Arabic.
 */
@Composable
private fun AboutRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { contentDescription = description; role = Role.Button },
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Hands a URI to whatever the device uses for it. رَنّة holds no INTERNET
 * permission and does not need one: opening a link is the browser's or the mail
 * client's work, not the app's.
 */
private fun Context.openUri(uri: String, failureMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }.onFailure {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

private const val LINKEDIN_URL = "https://www.linkedin.com/in/mutiri"
private const val EMAIL_URI = "mailto:mutirieng@gmail.com"
private const val GITHUB_URL = "https://github.com/Mod578/Rannah"

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
            content = content,
        )
    }
}
