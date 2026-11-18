package com.novalisten.app

import com.novalisten.app.data.Track

/** Metadata only: no real song, artist, file, API key or network request. */
internal fun playerTestTrack() = Track(
    id = "fixture", title = "Un titre synthétique volontairement très long pour ce petit écran",
    artist = "Un artiste de test au nom également très long", album = "Album synthétique", albumId = -1,
    albumArtist = "Test", durationMs = 180_000, dateAdded = 0, year = 0, number = 1,
    fileName = "fixture.wav", size = 0, mimeType = "audio/wav",
)
