package com.novalisten.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.io.OutputStream
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class NovaSmokeTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as NovaApplication
    private val fixtures = mutableListOf<Uri>()
    @Before fun prepare() {
        compose.runOnIdle { app.lyricPreferences.automatic(false); app.artistPreferences.biography(false); app.artistPreferences.portrait(false); app.themes.effects(false); app.themes.select("cyber"); app.repository.refresh() }
        compose.waitUntil(20_000) { app.repository.library.value.permission && !app.repository.library.value.loading }
    }
    @After fun cleanup() {
        runCatching { compose.runOnIdle {
            app.lyricPreferences.automatic(false); app.artistPreferences.biography(false); app.artistPreferences.portrait(false)
            androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java].stop()
        } }
        kotlinx.coroutines.runBlocking { app.repository.mutate {
            fixtures.forEach { uri ->
                writableDatabase.delete("lyrics", "track=?", arrayOf(uri.toString()))
                writableDatabase.delete("automatic_lyrics", "track=?", arrayOf(uri.toString()))
            }
        } }
        fixtures.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test fun freshStartupSelectsLibraryInsteadOfHome() {
        compose.onNodeWithTag("tab_1").assertIsSelected()
        compose.onNodeWithTag("track_list").assertExists()
    }

    @Test fun explicitPlayOpensFullscreenButNaturalAdvanceDoesNotReopenItAfterDismissal() {
        val first = makeAudio("Départ synthétique", 65)
        val second = makeAudio("Suite synthétique", 65)
        app.repository.refresh()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.waitUntil(20_000) { vm.playback.value.connected && app.repository.library.value.tracks.any { it.id == second.toString() } }
        val tracks = listOf(first, second).map { uri -> app.repository.library.value.tracks.first { it.id == uri.toString() } }
        compose.runOnIdle { vm.play(tracks, tracks.first()) }
        compose.onNodeWithTag("full_player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Réduire le player").performClick()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.runOnIdle { vm.seek(tracks.first().durationMs - 250) }
        compose.waitUntil(20_000) { vm.playback.value.currentId == second.toString() }
        compose.onNodeWithTag("full_player").assertDoesNotExist()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        compose.runOnIdle { vm.play(listOf(tracks.last()), tracks.last()) }
        compose.onNodeWithTag("full_player").assertIsDisplayed()
    }

    @Test fun bothCatalogsExposeAndRememberBothTrackCountSortDirections() {
        for ((tab, key) in listOf(2 to "artists_sort", 3 to "albums_sort")) {
            compose.onNodeWithTag("tab_$tab").performClick()
            for (value in listOf(CatalogSort.MOST_TRACKS, CatalogSort.FEWEST_TRACKS)) {
                compose.onNodeWithTag("catalog_sort").performClick()
                compose.onNodeWithTag("catalog_sort_${value.name}").performClick()
                assertEquals(value.name, context.getSharedPreferences("nova_catalog", 0).getString(key, ""))
            }
        }
        compose.activityRule.scenario.recreate()
        assertEquals(CatalogSort.FEWEST_TRACKS.name, context.getSharedPreferences("nova_catalog", 0).getString("albums_sort", ""))
    }

    @Test fun navigateAllSevenTabsAndThemeSurvivesRecreation() {
        for (index in 0..6) {
            compose.onNodeWithTag("navigation").performScrollToNode(hasTestTag("tab_$index"))
            compose.onNodeWithTag("tab_$index").performClick()
            compose.onNodeWithTag("tab_$index").assertIsSelected()
        }
        compose.onNodeWithContentDescription("Choisir le thème").performClick()
        compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("theme_cloud"))
        compose.onNodeWithTag("theme_cloud").performClick()
        compose.runOnIdle { assertEquals("cloud", app.themes.state.value.selected) }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("cloud", app.themes.state.value.selected) }
        screenshot("01-themes-cloud.png")
        // The title/back button belongs to the scrolling page header.
        compose.onNodeWithTag("theme_list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Retour").performClick()
        compose.onNodeWithTag("navigation").performScrollToNode(hasTestTag("tab_0"))
        compose.onNodeWithTag("tab_0").performClick()
        compose.onNodeWithContentDescription("Choisir le thème").performClick()
        compose.onNodeWithTag("theme_list").performScrollToNode(hasTestTag("theme_cyber"))
        compose.onNodeWithTag("theme_cyber").performClick()
        compose.onNodeWithContentDescription("Retour").performClick()
        screenshot("02-accueil-cyber.png")
    }
    @Test fun realMediaStoreAudioFiltersShortFilesAndPlaysOffline() {
        val long = makeAudio("NovaListen test 65", 65)
        makeAudio("NovaListen test 12", 12)
        app.repository.refresh()
        compose.waitUntil(20_000) { app.repository.library.value.tracks.any { it.id == long.toString() } }
        assertFalse(app.repository.library.value.tracks.any { it.fileName == "NovaListen test 12.wav" })
        compose.onNodeWithTag("navigation").performScrollToNode(hasTestTag("tab_1"))
        compose.onNodeWithTag("tab_1").performClick()
        // Regression: MaterialTheme alone does not provide LocalContentColor.
        // The default universe must render its title in light, readable text.
        val titleBitmap = compose.onNodeWithTag("page_title").captureToImage().asAndroidBitmap()
        val pixels = IntArray(titleBitmap.width * titleBitmap.height)
        titleBitmap.getPixels(pixels, 0, titleBitmap.width, 0, 0, titleBitmap.width, titleBitmap.height)
        assertTrue("Cyber Nova title must contain its light foreground, not black text", pixels.count { color -> (color ushr 16 and 255) > 180 && (color ushr 8 and 255) > 180 && (color and 255) > 180 } > 10)
        val tag = "track_${long.lastPathSegment}"
        compose.onNodeWithTag("track_list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("full_player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Réduire le player").performClick()
        compose.onNodeWithTag("mini_player").assertIsDisplayed()
        screenshot("03-bibliotheque-lecture.png")
        compose.onNodeWithTag("mini_player").performClick()
        compose.onNodeWithTag("full_player").assertIsDisplayed()
        assertPlayerControlsVisible()
        screenshot("08-pochette-commandes-degagees.png")
        // Physical directions: left opens lyrics; right returns to artwork. No wrap-around.
        compose.onNodeWithTag("player_content").performTouchInput { swipeRight() }
        compose.onNodeWithTag("player_cover_page").assertIsDisplayed()
        compose.onNodeWithTag("player_content").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("player_lyrics_page").assertIsDisplayed()
        assertPlayerControlsVisible()
        compose.onNodeWithTag("player_content").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("player_lyrics_page").assertIsDisplayed()
        compose.onNodeWithTag("player_content").performTouchInput { swipeRight() }
        compose.onNodeWithTag("player_cover_page").assertIsDisplayed()
        // The explicit lyrics button remains available too.
        compose.onNodeWithContentDescription("Afficher les paroles").performScrollTo().performClick()
        compose.onNodeWithText("Vos mots, votre musique").assertIsDisplayed()
        assertPlayerControlsVisible()
        screenshot("04-paroles-locales.png")
        // Opening online tools is still local. Test only synthetic text and a deliberately missing key.
        val requestsBefore = (app.remote.transport as com.novalisten.app.enrichment.RemoteHttp).requestCount
        assertFalse(app.translation.activated)
        kotlinx.coroutines.runBlocking {
            app.credentials.remove(com.novalisten.app.enrichment.SecretSlot.AZURE)
            app.repository.mutate { saveLyrics(long.toString(), com.novalisten.app.data.SavedLyrics("[00:01.000]Original synthetic test line", "Français manuel de test", "Fixture locale", "VF manuelle")) }
        }
        compose.runOnIdle { androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java].seek(1500) }
        compose.waitForIdle()
        compose.onNodeWithText("Pochette").assertDoesNotExist()
        compose.onNodeWithText("Rechercher").assertDoesNotExist()
        compose.onNodeWithText("Traduire").assertDoesNotExist()
        screenshot("07-paroles-epurees-centrees.png")
        compose.onNodeWithContentDescription("Actions du morceau").performClick()
        compose.onNodeWithTag("menu_search_lyrics").assertExists()
        compose.onNodeWithTag("track_menu").performScrollToNode(hasTestTag("menu_translate_lyrics"))
        compose.onNodeWithTag("menu_translate_lyrics").assertIsEnabled().performClick()
        compose.onNodeWithTag("enrichment_screen").assertIsDisplayed()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translate_local"))
        compose.onNodeWithTag("translate_local").assertIsNotEnabled()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translation_language_en"))
        compose.onNodeWithTag("translation_language_en").performClick()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translate_local"))
        compose.onNodeWithTag("translate_local").assertIsNotEnabled() // language alone does not authorize Google
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translation_local_consent"))
        compose.onNodeWithTag("translation_local_consent").performClick()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translate_local"))
        compose.onNodeWithTag("translate_local").assertIsEnabled() // not clicked: no SDK or pack activated
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translate_azure"))
        compose.onNodeWithTag("translate_azure").assertIsNotEnabled()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("azure_f0"))
        compose.onNodeWithTag("azure_f0").performClick()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translate_azure"))
        compose.onNodeWithTag("translate_azure").assertIsNotEnabled() // F0 alone does not authorize text transmission
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("azure_consent"))
        compose.onNodeWithTag("azure_consent").performClick()
        compose.onNodeWithTag("translation_screen").performScrollToNode(hasTestTag("translate_azure"))
        compose.onNodeWithTag("translate_azure").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("translate_azure").assertIsNotEnabled(); true }.getOrDefault(false) }
        assertEquals(requestsBefore, (app.remote.transport as com.novalisten.app.enrichment.RemoteHttp).requestCount)
        assertFalse(app.translation.activated)
        assertEquals("Français manuel de test", app.repository.data.value.lyrics[long.toString()]?.french)
        screenshot("06-traduction-consentements.png")
        compose.onNodeWithContentDescription("Fermer la recherche").performClick()
        compose.onNodeWithContentDescription("Réduire le player").performClick()
        compose.onNodeWithContentDescription("Pause").performClick()
    }
    @Test fun fullPlayerUsesTheRealActivityBoundsAfterRotationAndRecreation() {
        val uri = makeAudio("NovaListen viewport fixture", 65)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        app.repository.refresh()
        compose.waitUntil(20_000) { app.repository.library.value.tracks.any { it.id == uri.toString() } && vm.playback.value.connected }
        val track = app.repository.library.value.tracks.first { it.id == uri.toString() }
        compose.runOnIdle { vm.play(listOf(track), track) }
        compose.waitUntil(20_000) { vm.playback.value.currentId == track.id }
        compose.onNodeWithTag("full_player").assertIsDisplayed()
        assertPlayerControlsVisible()
        compose.onNodeWithTag("player_content").performTouchInput { swipeLeft() }
        assertPlayerControlsVisible()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            compose.waitUntil(20_000) {
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    compose.onAllNodesWithTag("full_player").fetchSemanticsNodes().size == 1
            }
            assertPlayerControlsVisible()
            screenshot("10-commandes-paysage-fenetre-reelle.png")
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertPlayerControlsVisible()
            compose.onNodeWithTag("player_content").performTouchInput { swipeRight() }
            compose.onNodeWithTag("player_cover_page").assertIsDisplayed()
            assertPlayerControlsVisible()
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @Test fun persistedNativeLyricsStayVisibleAfterClearingTheSessionAndRecreatingTheActivity() {
        val uri = makeAudio("NovaListen persisted lyrics fixture", 65)
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java]
        app.repository.refresh()
        compose.waitUntil(20_000) { app.repository.library.value.tracks.any { it.id == uri.toString() } && vm.playback.value.connected }
        val track = app.repository.library.value.tracks.first { it.id == uri.toString() }
        val text = "[00:01.000]Clarté nouvelle"
        val line = LyricTiming.line(1000, "Clarté nouvelle", listOf(TimedToken("Clarté", 1000, 1500), TimedToken("nouvelle", 1800, 2800)))
        val saved = SavedLyrics(text, originalSource = "Fixture persistante locale", originalTiming = LyricTiming.encode(text, listOf(line)))
        val record = AutomaticLyricRecord(track.lyricIdentity(), saved)
        val before = (app.remote.transport as RemoteHttp).requestCount
        kotlinx.coroutines.runBlocking { assertTrue(app.repository.saveAutomaticLyrics(track.id, record)) }
        compose.runOnIdle { app.lyricPreferences.automatic(true); vm.play(listOf(track), track) }
        compose.waitUntil(20_000) { vm.playback.value.currentId == track.id && vm.playback.value.playing && app.automaticLyrics.state.value.status == AutoLyricStatus.SAVED }
        // This test checks storage/recreation, not an intentionally continuous draw animation.
        // Pause before opening native karaoke so Espresso can synchronize deterministically;
        // the separate pixel tests exercise its native start/middle/end and seek rendering.
        compose.runOnIdle { vm.toggle() }
        compose.waitUntil(20_000) { !vm.playback.value.playing }
        compose.onNodeWithTag("full_player").assertIsDisplayed()
        compose.onNodeWithTag("player_content").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("lyric_line_0").assertIsDisplayed()
        assertPlayerControlsVisible()
        compose.runOnIdle {
            assertEquals(saved, vm.lyricsFor(track.id))
            app.automaticLyrics.stop() // deliberately throw away the session, not the durable SQLite record
            assertEquals(saved, vm.lyricsFor(track.id))
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("player_lyrics_page").assertIsDisplayed()
        compose.onNodeWithTag("lyric_line_0").assertIsDisplayed()
        assertPlayerControlsVisible()
        compose.runOnIdle {
            val restored = androidx.lifecycle.ViewModelProvider(compose.activity)[MainViewModel::class.java].lyricsFor(track.id)
            assertEquals(saved, restored)
            assertEquals(line.words, LyricTiming.decode(restored.original, restored.originalTiming).single().words)
            assertEquals(before, (app.remote.transport as RemoteHttp).requestCount)
        }
        screenshot("09-paroles-et-synchronisation-conservees.png")
    }

    @Test fun customEditorExposesAllSixCategories() {
        compose.onNodeWithContentDescription("Choisir le thème").performClick()
        compose.onNodeWithTag("custom_theme").performClick()
        for (label in listOf("01 / Couleurs", "02 / Polices", "03 / Icônes", "04 / Formes", "05 / Animations", "06 / Styles de courbes")) {
            compose.onNodeWithTag("custom_editor").performScrollToNode(hasText(label)); compose.onNodeWithText(label).assertIsDisplayed()
        }
        screenshot("05-editeur-personnalise.png")
    }
    private fun makeAudio(title: String, seconds: Int): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav"); put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, "Test local"); put(MediaStore.Audio.Media.ALBUM, "Tests NovaListen")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav"); put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.DURATION, seconds * 1000L)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/NovaListenTests"); put(MediaStore.Audio.Media.IS_PENDING, 1) }
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        fixtures += uri
        context.contentResolver.openOutputStream(uri)!!.buffered().use { output ->
            fun le(value: Int, count: Int) { repeat(count) { output.write(value ushr (it * 8) and 255) } }
            val rate = 8000; val dataSize = seconds * rate * 2
            output.write("RIFF".toByteArray()); le(36 + dataSize, 4); output.write("WAVEfmt ".toByteArray()); le(16, 4); le(1, 2); le(1, 2); le(rate, 4); le(rate * 2, 4); le(2, 2); le(16, 2); output.write("data".toByteArray()); le(dataSize, 4)
            repeat(seconds * rate) { sample -> le((sin(sample * 2 * Math.PI * 220 / rate) * 500).toInt(), 2) }
        }
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun assertPlayerControlsVisible() {
        assertPlayerInsidePhysicalWindow(compose, compose.activity)
        val safe = compose.onNodeWithTag("player_safe_area").getUnclippedBoundsInRoot()
        val full = compose.onNodeWithTag("full_player").getUnclippedBoundsInRoot()
        for (tag in listOf("player_repeat", "player_previous", "player_toggle", "player_next", "player_queue")) {
            val button = compose.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag must be fully inside safe horizontal bounds", button.left >= safe.left && button.right <= safe.right)
            assertTrue("$tag must be fully inside safe vertical bounds", button.top >= safe.top && button.bottom <= safe.bottom)
            assertTrue("$tag must not touch the screen bottom", full.bottom.value - button.bottom.value >= 20f)
        }
    }
    private fun screenshot(name: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.getExternalFilesDir("evidence")!!
        dir.mkdirs()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(dir, name))
    }
}
