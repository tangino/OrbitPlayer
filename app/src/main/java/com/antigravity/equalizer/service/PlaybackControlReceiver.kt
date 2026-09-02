package com.antigravity.equalizer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.equalizer.audio.MusicPlayerManager

class PlaybackControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val playerManager = MusicPlayerManager.getInstance(context)
        Log.i(TAG, "PlaybackControlReceiver onReceive action: $action")

        when (action) {
            MusicPlaybackService.ACTION_PREV -> playerManager.playPrevious()
            MusicPlaybackService.ACTION_PLAY_PAUSE -> playerManager.togglePlayPause()
            MusicPlaybackService.ACTION_NEXT -> playerManager.playNext()
        }
    }

    companion object {
        private const val TAG = "PlaybackControlReceiver"
    }
}
