package xyz.blacksheep.mjolnir.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Modal dialog presenting basic information about the Mjolnir application.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "N/A"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    val uriHandler = LocalUriHandler.current
    val githubUrl = "https://github.com/riap0526/mjolnir"
    val upstreamUrl = "https://github.com/blacksheepmvp/mjolnir"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Mjolnir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version $versionName")
                Text(
                    text = githubUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.clickable {
                        uriHandler.openUri(githubUrl)
                    }
                )
                Text("Personal-use fork based on the original Mjolnir by blacksheepmvp:")
                Text(
                    text = upstreamUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.clickable {
                        uriHandler.openUri(upstreamUrl)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
