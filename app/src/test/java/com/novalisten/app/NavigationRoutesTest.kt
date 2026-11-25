package com.novalisten.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Un écran n'existe que si la route qui le désigne a une branche dans le `when (route)`.
 * Sans cette vérification, un bouton peut changer l'état sans rien afficher : l'application
 * paraît « ne rien faire » au clic, et aucun test de compilation ne le signale.
 */
class NavigationRoutesTest {
    private val routeRequested = Regex("route\\s*=\\s*\"([^\"]*)\"")
    private val routeHandled = Regex("^\\s*\"([^\"]+)\"\\s*->", RegexOption.MULTILINE)
    private val splitBranch = Regex("^\\s*\"split\"\\s*->\\s*SplitSettingsScreen\\(", RegexOption.MULTILINE)

    private fun source(): String {
        val candidates = listOf(
            "app/src/main/java/com/novalisten/app/ui/NovaApp.kt",
            "src/main/java/com/novalisten/app/ui/NovaApp.kt",
        ).map(::File)
        val file = candidates.firstOrNull { it.exists() }
        requireNotNull(file) { "NovaApp.kt introuvable depuis ${File(".").absolutePath}" }
        return file.readText()
    }

    /** Toutes les routes réellement demandées par un bouton ou un écran. */
    private fun requested(text: String): Set<String> =
        routeRequested.findAll(text).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toSet()

    /** Toutes les routes que le `when (route)` sait afficher. */
    private fun handled(text: String): Set<String> =
        routeHandled.findAll(text).map { it.groupValues[1] }.toSet()

    @Test fun everyRequestedRouteHasAScreenToShow() {
        val text = source()
        val missing = requested(text) - handled(text)
        assertTrue("Routes demandées sans écran, donc un clic sans effet : $missing", missing.isEmpty())
    }

    @Test fun theSplitSettingsScreenIsActuallyReachable() {
        val text = source()
        assertTrue("La route split n'est pas demandée par les Paramètres.", "split" in requested(text))
        assertTrue("La route split n'a pas de branche : l'écran ne s'ouvrira jamais.", "split" in handled(text))
        assertTrue("La branche split doit construire SplitSettingsScreen.", splitBranch.containsMatchIn(text))
    }
}
