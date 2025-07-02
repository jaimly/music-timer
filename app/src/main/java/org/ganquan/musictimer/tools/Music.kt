package org.ganquan.musictimer.tools

import android.media.MediaPlayer
import java.io.File
import kotlin.math.floor

class Music {
    companion object {
        private var mediaPlayer: MediaPlayer = MediaPlayer()
        fun play(path: String) {
            mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepare()
            mediaPlayer.start()
        }

        fun playCircle(fileList: List<File>, name: String = "") {
            try {
                var file: File? = fileList.find { it.name == name }
                if (file == null) file = fileList[floor(Math.random() * fileList.size).toInt()]
                play(file.path)
                mediaPlayer.setOnCompletionListener {
                    mediaPlayer.release()
                    playCircle(fileList, name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop() {
            mediaPlayer.setOnCompletionListener {}
            mediaPlayer.release()
        }
    }
}