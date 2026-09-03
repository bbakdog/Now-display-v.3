package com.example.nowplaying

import android.content.ComponentName
import android.service.dreams.DreamService

/**
 * 설정 > 디스플레이 > 화면 보호기 에서 선택할 수 있는 화면보호기(Daydream) 구현.
 * 충전 중 대기 상태가 되면 시스템이 자동으로 이 서비스를 실행한다.
 */
class NowPlayingDreamService : DreamService() {

    private lateinit var controller: NowPlayingController

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = true          // 터치로 재생/일시정지 등 버튼 조작 가능하게
        isFullscreen = true
        setContentView(R.layout.view_now_playing)

        val listenerComponent = ComponentName(this, MediaNotificationListenerService::class.java)
        controller = NowPlayingController(this, findViewById(R.id.rootContainer), listenerComponent)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        controller.start()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        controller.stop()
    }
}
