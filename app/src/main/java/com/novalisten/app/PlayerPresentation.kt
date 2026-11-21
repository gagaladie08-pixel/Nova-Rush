package com.novalisten.app

const val INITIAL_TAB = 1

data class PlayerOpenRequest(val token: String = "", val trackId: String = "")

/** Save both fields together: a dismissed request must not be replayed on recreation. */
data class PlayerPresentation(val open: Boolean = false, val consumedToken: String = "") {
    fun accept(request: PlayerOpenRequest): PlayerPresentation =
        if (request.token.isBlank() || request.trackId.isBlank() || request.token == consumedToken) this
        else copy(open = true, consumedToken = request.token)
    fun dismiss(): PlayerPresentation = copy(open = false)
    fun openManually(): PlayerPresentation = copy(open = true)
    fun onPlayback(currentId: String?): PlayerPresentation = if (currentId == null) dismiss() else this
}
