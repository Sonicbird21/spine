package com.spotify.music.hooks.spotify.features.network.session

object SessionState {
    
    @Volatile
    var isAuthenticatedSession: Boolean = false
}