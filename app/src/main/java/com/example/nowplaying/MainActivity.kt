package com.example.nowplaying

import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var controller: NowPlayingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_now_playing)

        // 화면을 항상 켜진 상태로 유지 (거치대에 놓고 보는 용도)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        keepFullscreen()

        val listenerComponent = ComponentName(this, MediaNotificationListenerService::class.java)
        controller = NowPlayingController(this, findViewById(R.id.rootContainer), listenerComponent)
    }

    private fun keepFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onResume() {
        super.onResume()
        controller.start()
    }

    override fun onPause() {
        super.onPause()
        controller.stop()
    }
}
