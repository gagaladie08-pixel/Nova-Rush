package com.novalisten.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import com.novalisten.app.enrichment.RemoteSafety
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.novalisten.app.R

/** Official unmodified Google badge, bundled locally so attribution works offline. */
@Composable
fun GoogleTranslationAttribution() {
    val handler = LocalUriHandler.current
    Surface(color = Color.White, modifier = Modifier.padding(vertical = 8.dp).clickable { runCatching { handler.openUri("https://translate.google.com/") } }) {
        Image(painterResource(R.drawable.google_translate_attribution), "powered by Google Translate · traduction automatique", Modifier.padding(8.dp).width(176.dp).height(16.dp))
    }
}

/** Persisted credits are still useful offline; opening the original page is a separate action. */
@Composable
fun SavedAttribution(source: String) {
    val handler = LocalUriHandler.current
    val link = source.lineSequence().map(String::trim).firstOrNull { line ->
        line.startsWith("https://") && runCatching { RemoteSafety.https(line) }.isSuccess
    }
    Column {
        Text(source, style = MaterialTheme.typography.bodySmall)
        if (link != null) TextButton({ runCatching { handler.openUri(link) } }) { Text("Ouvrir la source d’origine") }
    }
}
