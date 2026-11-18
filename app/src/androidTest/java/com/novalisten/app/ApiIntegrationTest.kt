package com.novalisten.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novalisten.app.data.NovaDatabase
import com.novalisten.app.data.SavedLyrics
import com.novalisten.app.enrichment.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as NovaApplication
    @Before fun keepFixturesOffline() { compose.runOnIdle { app.lyricPreferences.automatic(false) } }
    @Test fun settingsKeysAreEncryptedPersistentDeletableAndDoNotTriggerNetwork() {
        val onlyTestValue = "novalisten-fixture-not-a-real-key-927" // generated test data, never a provider credential
        val http = app.remote.transport as RemoteHttp
        val before = http.requestCount
        compose.onNodeWithContentDescription("Réglages").performClick()
        compose.onNodeWithText("API et clés").performClick()
        compose.onNodeWithTag("api_settings").performScrollToNode(hasTestTag("credential_LASTFM"))
        compose.onNodeWithTag("credential_edit_LASTFM").performClick()
        compose.onNodeWithTag("credential_input").performTextInput(onlyTestValue)
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        compose.onNodeWithTag("credential_save").performClick()
        compose.waitUntil(10000) { app.credentials.status.value[SecretSlot.LASTFM] == CredentialStatus.STORED }
        val disk = context.getSharedPreferences("api_credentials_v1", Context.MODE_PRIVATE).getString(SecretSlot.LASTFM.name, "")!!
        assertFalse(disk.contains(onlyTestValue)); assertTrue(disk.startsWith("1:"))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(onlyTestValue, runBlocking { app.credentials.get(SecretSlot.LASTFM) })
        compose.onNodeWithTag("api_settings").performScrollToNode(hasTestTag("credential_LASTFM"))
        compose.onNodeWithTag("credential_delete_LASTFM").performClick()
        compose.waitUntil(10000) { app.credentials.status.value[SecretSlot.LASTFM] == CredentialStatus.ABSENT }
        assertNull(runBlocking { app.credentials.get(SecretSlot.LASTFM) })
        assertEquals("Opening settings and editing a key must not issue an API request", before, http.requestCount)
        compose.runOnIdle { assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
    }
    @Test fun unsavedCredentialIsNotRestoredAcrossRecreation() {
        compose.onNodeWithContentDescription("Réglages").performClick()
        compose.onNodeWithText("API et clés").performClick()
        compose.onNodeWithTag("api_settings").performScrollToNode(hasTestTag("credential_GENIUS"))
        compose.onNodeWithTag("credential_edit_GENIUS").performClick()
        compose.onNodeWithTag("credential_input").performTextInput("unsaved-test-only")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("credential_input").assertDoesNotExist()
        compose.onNodeWithTag("api_settings").performScrollToNode(hasTestTag("credential_GENIUS"))
        compose.onNodeWithTag("credential_edit_GENIUS").performClick()
        compose.onNodeWithTag("credential_input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithText("Annuler").performClick()
    }
    @Test fun vaultDetectsTamperingWithoutDeletingOrReturningPlaintext() = runBlocking {
        val namespace = "vault_instrumentation_test"
        val vault = CredentialVault(context, namespace)
        try {
            vault.save(SecretSlot.GENIUS, "test-only-first")
            vault.save(SecretSlot.LASTFM, "test-only-second")
            val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
            // Moving ciphertext between providers must fail authenticated AAD verification.
            val encrypted = prefs.getString(SecretSlot.GENIUS.name, null)!!
            prefs.edit().putString(SecretSlot.LASTFM.name, encrypted).commit()
            try { vault.get(SecretSlot.LASTFM); fail("Tampered record must not decrypt") } catch (_: RemoteFailure) { }
            assertEquals(encrypted, prefs.getString(SecretSlot.LASTFM.name, null))
            vault.refresh(); assertEquals(CredentialStatus.UNREADABLE, vault.status.value[SecretSlot.LASTFM])
            assertEquals("test-only-first", vault.get(SecretSlot.GENIUS))
            vault.save(SecretSlot.LASTFM, "test-only-replacement")
            assertEquals("test-only-replacement", vault.get(SecretSlot.LASTFM))
        } finally { vault.clear() }
    }
    @Test fun databaseV1MigratesWithoutErasingSongsNotesOrLyrics() {
        val name = "api_migration_instrumentation.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE flags (track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, tracks TEXT NOT NULL, created INTEGER NOT NULL, pinned INTEGER, cover TEXT)")
            db.execSQL("CREATE TABLE listens (id INTEGER PRIMARY KEY AUTOINCREMENT, track TEXT NOT NULL, at INTEGER NOT NULL, zone TEXT NOT NULL, local_date TEXT NOT NULL, local_hour INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE lyrics (track TEXT PRIMARY KEY, original TEXT NOT NULL, french TEXT NOT NULL)")
            db.execSQL("CREATE TABLE artists (id TEXT PRIMARY KEY, bio TEXT NOT NULL, source TEXT NOT NULL, portrait TEXT)")
            db.execSQL("INSERT INTO flags VALUES ('local-test', 1, 0)")
            db.execSQL("INSERT INTO lyrics VALUES ('local-test', '[00:01.000]Original de test', '[00:01.000]Français de test')")
            db.execSQL("INSERT INTO artists VALUES ('artist-test', 'Biographie de test', 'Source locale de test', NULL)")
            db.version = 1
        }
        try {
            NovaDatabase(context, name).use { db ->
                val snapshot = db.snapshot()
                assertTrue("local-test" in snapshot.favorites)
                assertEquals("[00:01.000]Original de test", snapshot.lyrics["local-test"]?.original)
                assertEquals("[00:01.000]Français de test", snapshot.lyrics["local-test"]?.french)
                assertEquals("", snapshot.lyrics["local-test"]?.originalSource)
                assertEquals("Biographie de test", snapshot.artists["artist-test"]?.biography)
                db.saveLyrics("local-test", SavedLyrics("VO nouvelle", "VF nouvelle", "Source VO", "Source VF"))
                assertEquals("Source VF", db.snapshot().lyrics["local-test"]?.frenchSource)
                assertTrue(db.snapshot().covers.isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun databaseV2AddsWordTimingWithoutDroppingTheSavedVoVfOrCredits() {
        val name = "lyrics_v2_migration_test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE flags (track TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, tracks TEXT NOT NULL, created INTEGER NOT NULL, pinned INTEGER, cover TEXT)")
            db.execSQL("CREATE TABLE listens (id INTEGER PRIMARY KEY AUTOINCREMENT, track TEXT NOT NULL, at INTEGER NOT NULL, zone TEXT NOT NULL, local_date TEXT NOT NULL, local_hour INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE lyrics (track TEXT PRIMARY KEY, original TEXT NOT NULL, french TEXT NOT NULL, original_source TEXT NOT NULL DEFAULT '', french_source TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE artists (id TEXT PRIMARY KEY, bio TEXT NOT NULL, source TEXT NOT NULL, portrait TEXT, portrait_source TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE album_covers (id TEXT PRIMARY KEY, uri TEXT NOT NULL, source TEXT NOT NULL)")
            db.execSQL("CREATE TABLE identities (id TEXT PRIMARY KEY, mbid TEXT NOT NULL)")
            db.execSQL("INSERT INTO lyrics VALUES ('fixture', '[00:01.000]Clarté nouvelle', 'VF choisie', 'Source VO', 'Source VF')")
            db.version = 2
        }
        try { NovaDatabase(context, name).use { db ->
            val old = db.snapshot().lyrics.getValue("fixture")
            assertEquals("VF choisie", old.french); assertEquals("Source VO", old.originalSource); assertEquals("Source VF", old.frenchSource)
            assertEquals("", old.originalTiming)
            val line = com.novalisten.app.data.LyricTiming.line(1000, "Clarté nouvelle", listOf(com.novalisten.app.data.TimedToken("Clarté", 1000, 1500), com.novalisten.app.data.TimedToken("nouvelle", 1800, 2800)))
            val native = old.copy(originalTiming = com.novalisten.app.data.LyricTiming.encode(old.original, listOf(line)))
            db.saveLyrics("fixture", native)
            assertEquals(native, db.snapshot().lyrics.getValue("fixture"))
            assertEquals(line.words, com.novalisten.app.data.LyricTiming.decode(native.original, native.originalTiming).single().words)
            assertEquals(3, db.readableDatabase.version)
        } } finally { context.deleteDatabase(name) }
    }
    @Test fun automaticLyricsPreferenceIsPersistentAndDoesNotEnableTranslationOrCallAService() {
        val before = (app.remote.transport as RemoteHttp).requestCount
        compose.onNodeWithContentDescription("Réglages").performClick()
        compose.onNodeWithText("API et clés").performClick()
        compose.onNodeWithTag("api_settings").performScrollToNode(hasTestTag("automatic_lyrics"))
        compose.onNodeWithTag("automatic_lyrics").assertIsOff().performClick().assertIsOn()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("api_settings").performScrollToNode(hasTestTag("automatic_lyrics"))
        compose.onNodeWithTag("automatic_lyrics").assertIsOn().performClick().assertIsOff()
        assertFalse(app.translation.activated)
        assertEquals(before, (app.remote.transport as RemoteHttp).requestCount)
    }
    @Test fun photoPreviewsReuseLocalBytesAndFingerprintDuplicateImages() = runBlocking {
        val bitmap = android.graphics.Bitmap.createBitmap(320, 320, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(24, 135, 67))
        val bytes = java.io.ByteArrayOutputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
        bitmap.recycle()
        var downloads = 0
        val transport = object : RemoteTransport {
            override suspend fun request(provider: Provider, path: String, parameters: Map<String, String>, headers: Map<String, String>, jsonBody: String?): String = error("No API request")
            override suspend fun image(url: String, maxBytes: Int): ByteArray { downloads++; return bytes }
        }
        val images = RemoteImages(context, transport)
        images.clearPreviews()
        val one = ImageCandidate("test:one", "Image synthétique", "https://example.org/test/one-preview.png", "https://example.org/test/one.png", Credit("Fixture", "https://example.org/"), true)
        var saved: java.io.File? = null
        try {
            val first = images.preparePhoto(one)
            val again = images.preparePhoto(one)
            assertEquals(first.fingerprint, again.fingerprint); assertEquals(1, downloads)
            val duplicate = images.preparePhoto(one.copy(id = "test:two", preview = "https://example.org/test/two-preview.png", original = "https://example.org/test/two.png"))
            assertTrue(first.fingerprint.resembles(duplicate.fingerprint))
            val result = PhotoDiscovery(images::preparePhoto).discover(listOf(one, duplicate.image))
            assertEquals(1, result.photos.size); assertEquals(1, result.probableDuplicates); assertEquals(2, downloads)
            val uri = images.save(one)
            saved = java.io.File(java.net.URI(uri))
            assertTrue(saved.isFile)
            assertTrue(saved.canonicalPath.startsWith(java.io.File(context.filesDir, "images").canonicalPath + "/"))
            assertEquals(3, downloads)
        } finally { saved?.delete(); images.clearPreviews() }
    }
    @Test fun rejectedImageAssignmentDoesNotSilentlyReplaceLocalContent() {
        val name = "api_save_failure_instrumentation.db"
        context.deleteDatabase(name)
        try {
            NovaDatabase(context, name).use { db ->
                db.saveArtist("test", com.novalisten.app.data.ArtistNote(biography = "Texte local de test", portrait = "file:///local-test.jpg", portraitSource = "Crédit local"))
                db.writableDatabase.execSQL("CREATE TEMP TRIGGER fail_artist BEFORE INSERT ON artists BEGIN SELECT RAISE(ABORT, 'fixture rejection'); END")
                try { db.saveArtist("test", com.novalisten.app.data.ArtistNote(portrait = "file:///new-test.jpg")); fail("A failed DB write must be reported") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
                val old = db.snapshot().artists.getValue("test")
                assertEquals("file:///local-test.jpg", old.portrait); assertEquals("Crédit local", old.portraitSource)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun mergedManifestDoesNotAutoinitializeMlKitOrRequestMicrophone() {
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS)
        assertFalse(info.providers.orEmpty().any { it.name.contains("MlKitInitProvider") })
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        assertFalse(info.requestedPermissions.orEmpty().any { it.contains("RECORD_AUDIO") || it.contains("ACCESS_FINE_LOCATION") })
    }
}
