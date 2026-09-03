package com.example.nowplaying

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 유튜브뮤직 등 다른 앱이 재생 중인 미디어세션(MediaController)에 연결해서
 * 앨범아트 / 곡정보 / 재생바 / 이전-재생-다음 버튼을 갱신하는 공용 컨트롤러.
 * 상단 시계/배터리, 그리고 "시계 화면" 모드 전환도 함께 관리한다.
 *
 * MainActivity와 NowPlayingDreamService(화면보호기) 양쪽에서 동일하게 재사용한다.
 */
class NowPlayingController(
    private val context: Context,
    private val rootView: View,
    private val listenerComponent: ComponentName
) {

    // 큰 화면(Mode A)
    private val imgBackground: ImageView = rootView.findViewById(R.id.imgBackground)
    private val contentColumn: View = rootView.findViewById(R.id.contentColumn)
    private val imgAlbumArt: ImageView = rootView.findViewById(R.id.imgAlbumArt)
    private val cardVinyl: View = rootView.findViewById(R.id.cardVinyl)
    private val imgVinylArt: ImageView = rootView.findViewById(R.id.imgVinylArt)
    private val txtTitle: TextView = rootView.findViewById(R.id.txtTitle)
    private val txtArtist: TextView = rootView.findViewById(R.id.txtArtist)
    private val txtSource: TextView = rootView.findViewById(R.id.txtSource)
    private val seekBar: SeekBar = rootView.findViewById(R.id.seekBar)
    private val txtPosition: TextView = rootView.findViewById(R.id.txtPosition)
    private val txtDuration: TextView = rootView.findViewById(R.id.txtDuration)
    private val btnPrev: ImageButton = rootView.findViewById(R.id.btnPrev)
    private val btnPlayPause: ImageButton = rootView.findViewById(R.id.btnPlayPause)
    private val btnNext: ImageButton = rootView.findViewById(R.id.btnNext)
    private val permissionPrompt: View = rootView.findViewById(R.id.permissionPrompt)
    private val txtWaiting: View = rootView.findViewById(R.id.txtWaiting)
    private val btnGrantPermission: View = rootView.findViewById(R.id.btnGrantPermission)

    // 상단 상태바(시계/배터리) - 항상 표시
    private val txtClock: TextView = rootView.findViewById(R.id.txtClock)
    private val txtBatteryPercent: TextView = rootView.findViewById(R.id.txtBatteryPercent)
    private val imgBattery: ImageView = rootView.findViewById(R.id.imgBattery)
    private val batteryPill: View = rootView.findViewById(R.id.batteryPill)

    // 모드 전환 버튼 + 시계 화면(Mode B)
    private val btnToggleMode: ImageButton = rootView.findViewById(R.id.btnToggleMode)
    private val clockModeGroup: View = rootView.findViewById(R.id.clockModeGroup)
    private val txtBigClock: TextView = rootView.findViewById(R.id.txtBigClock)
    private val txtBigDate: TextView = rootView.findViewById(R.id.txtBigDate)
    private val miniPlayerRow: View = rootView.findViewById(R.id.miniPlayerRow)
    private val txtMiniTitle: TextView = rootView.findViewById(R.id.txtMiniTitle)
    private val txtMiniArtist: TextView = rootView.findViewById(R.id.txtMiniArtist)
    private val btnMiniPrev: ImageButton = rootView.findViewById(R.id.btnMiniPrev)
    private val btnMiniPlayPause: ImageButton = rootView.findViewById(R.id.btnMiniPlayPause)
    private val btnMiniNext: ImageButton = rootView.findViewById(R.id.btnMiniNext)

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var activeController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private var isClockMode = false

    private val clockFormat = SimpleDateFormat("H:mm", Locale.KOREA)
    private val dateFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREA)

    /** CD 회전: 목표 속도를 향해 서서히 가속/감속하며 도는 수동 회전 애니메이션 */
    private var vinylVelocity = 0f          // 현재 각속도 (도/프레임)
    private var vinylTargetVelocity = 0f    // 목표 각속도 (재생 중이면 최고 속도, 아니면 0)
    private val vinylFullSpeed = 3f       // 최고 속도일 때 각속도 (약 0.9초에 한 바퀴)
    private val vinylEasing = 0.08f         // 클수록 가속/감속이 빨라짐

    private val vinylTicker = object : Runnable {
        override fun run() {
            vinylVelocity += (vinylTargetVelocity - vinylVelocity) * vinylEasing
            if (vinylVelocity > 0.01f || vinylTargetVelocity > 0f) {
                cardVinyl.rotation = (cardVinyl.rotation + vinylVelocity) % 360f
            }
            handler.postDelayed(this, 16)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }

        override fun onSessionDestroyed() {
            activeController = null
            refreshFromActiveSessions()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            pickBestController(controllers ?: emptyList())
        }

    private val progressTicker = object : Runnable {
        override fun run() {
            tickProgress()
            tickClock()
            handler.postDelayed(this, 500)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            if (level < 0 || scale <= 0) return
            val percent = (level * 100 / scale)
            val isCharging = plugged != 0
            txtBatteryPercent.text = percent.toString()
            imgBattery.setImageResource(if (isCharging) R.drawable.ic_battery_charging else R.drawable.ic_battery)
            batteryPill.setBackgroundResource(if (isCharging) R.drawable.bg_pill_charging else R.drawable.bg_pill)
        }
    }

    init {
        btnPrev.setOnClickListener { activeController?.transportControls?.skipToPrevious() }
        btnNext.setOnClickListener { activeController?.transportControls?.skipToNext() }
        btnMiniPrev.setOnClickListener { activeController?.transportControls?.skipToPrevious() }
        btnMiniNext.setOnClickListener { activeController?.transportControls?.skipToNext() }
        val playPauseClick = View.OnClickListener {
            val state = activeController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                activeController?.transportControls?.pause()
            } else {
                activeController?.transportControls?.play()
            }
        }
        btnPlayPause.setOnClickListener(playPauseClick)
        btnMiniPlayPause.setOnClickListener(playPauseClick)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) txtPosition.text = formatMillis(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserSeeking = false
                activeController?.transportControls?.seekTo(sb?.progress?.toLong() ?: 0)
            }
        })
        btnGrantPermission.setOnClickListener {
            context.startActivity(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        btnToggleMode.setOnClickListener {
            val toClock = !isClockMode
            val musicView = musicAreaView()
            if (toClock) {
                playSlideTransition(outgoing = musicView, incoming = clockModeGroup)
            } else {
                playSlideTransition(outgoing = clockModeGroup, incoming = musicView)
            }
            isClockMode = toClock
            updateToggleIcon()
        }
    }

    /** 화면이 보이기 시작할 때 호출 */
    fun start() {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        handler.post(vinylTicker)
        applyModeVisibility()
        updateToggleIcon()

        if (!isNotificationAccessGranted()) {
            showPermissionPrompt()
        } else {
            permissionPrompt.visibility = View.GONE
            try {
                sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listenerComponent)
                refreshFromActiveSessions()
            } catch (e: SecurityException) {
                showPermissionPrompt()
            }
        }
        handler.post(progressTicker)
    }

    /** 화면이 사라질 때 호출 (리스너/핸들러 정리) */
    fun stop() {
        handler.removeCallbacks(progressTicker)
        handler.removeCallbacks(vinylTicker)
        activeController?.unregisterCallback(controllerCallback)
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {
        }
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    /** 큰 화면 모드인지 / 시계 화면 모드인지에 따라 관련 그룹의 표시 여부를 정리 */
    private fun applyModeVisibility() {
        if (isClockMode) {
            clockModeGroup.visibility = View.VISIBLE
            contentColumn.visibility = View.GONE
            permissionPrompt.visibility = View.GONE
            txtWaiting.visibility = View.GONE
        } else {
            clockModeGroup.visibility = View.GONE
            // 큰 화면 쪽 상태(권한 안내 / 대기중 / 재생정보)는 아래 각 함수가 다시 정리한다.
            if (activeController == null) {
                if (isNotificationAccessGranted()) showWaiting() else showPermissionPrompt()
            } else {
                updateMetadata(activeController?.metadata)
                updatePlaybackState(activeController?.playbackState)
            }
        }
    }

    /** 지금 보여야 하는 "음악 쪽" 화면(권한안내/대기중/재생정보) 중 하나를 반환 */
    private fun musicAreaView(): View = when {
        activeController == null && !isNotificationAccessGranted() -> permissionPrompt
        activeController == null -> txtWaiting
        else -> contentColumn
    }

    /** 전환 버튼 아이콘: 지금이 시계 화면이면 "음악으로 전환" 아이콘을, 음악 화면이면 "시계로 전환" 아이콘을 보여준다 */
    private fun updateToggleIcon() {
        btnToggleMode.setImageResource(
            if (isClockMode) R.drawable.ic_music_note else R.drawable.ic_clock_toggle
        )
    }

    /** 음악 화면으로 갈 때는 위로 올라오고, 나가는 화면은 아래로 내려가며 사라지는 슬라이드 전환 */
    private fun playSlideTransition(outgoing: View, incoming: View) {
        if (outgoing === incoming) return
        val distance = rootView.resources.displayMetrics.heightPixels * 0.45f

        incoming.translationY = distance
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE
        incoming.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        outgoing.animate()
            .translationY(distance)
            .alpha(0f)
            .setDuration(320)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.translationY = 0f
                outgoing.alpha = 1f
            }
            .start()
    }

    private fun showPermissionPrompt() {
        if (isClockMode) return
        permissionPrompt.visibility = View.VISIBLE
        txtWaiting.visibility = View.GONE
        contentColumn.visibility = View.GONE
    }

    private fun refreshFromActiveSessions() {
        try {
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            pickBestController(controllers)
        } catch (e: SecurityException) {
            showPermissionPrompt()
        }
    }

    /** 유튜브뮤직 세션을 우선하고, 없으면 재생 중인 아무 세션이나 선택 */
    private fun pickBestController(controllers: List<MediaController>) {
        if (controllers.isEmpty()) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = null
            showWaiting()
            miniPlayerRow.visibility = View.GONE
            return
        }
        val youtubeMusic = controllers.firstOrNull {
            it.packageName == "com.google.android.apps.youtube.music"
        }
        val playing = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        val chosen = youtubeMusic ?: playing ?: controllers.first()

        if (chosen != activeController) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = chosen
            chosen.registerCallback(controllerCallback, handler)
            txtSource.text = appLabelFor(chosen.packageName)
            updateMetadata(chosen.metadata)
            updatePlaybackState(chosen.playbackState)
        }
    }

    private fun appLabelFor(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun showWaiting() {
        if (isClockMode) return
        permissionPrompt.visibility = View.GONE
        txtWaiting.visibility = View.VISIBLE
        contentColumn.visibility = View.GONE
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata == null) return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""

        if (!isClockMode) {
            permissionPrompt.visibility = View.GONE
            txtWaiting.visibility = View.GONE
            contentColumn.visibility = View.VISIBLE
        }

        txtTitle.text = title
        txtArtist.text = artist
        txtMiniTitle.text = title
        txtMiniArtist.text = artist
        miniPlayerRow.visibility = View.VISIBLE

        val art: Bitmap? =
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

        if (art != null) {
            imgAlbumArt.setImageBitmap(art)
            imgVinylArt.setImageBitmap(art)
            imgBackground.setImageBitmap(art)
            applyBackgroundBlur()
        }

        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (duration > 0) {
            seekBar.max = duration.toInt()
            txtDuration.text = formatMillis(duration)
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        if (state == null) return
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnPlayPause.setImageResource(icon)
        btnMiniPlayPause.setImageResource(icon)
        vinylTargetVelocity = if (isPlaying) vinylFullSpeed else 0f
    }

    private fun tickProgress() {
        val controller = activeController ?: return
        val state = controller.playbackState ?: return
        if (isUserSeeking) return

        val elapsedSincePositionUpdate =
            if (state.state == PlaybackState.STATE_PLAYING) {
                (android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed
            } else 0f

        val currentPosition = (state.position + elapsedSincePositionUpdate).toLong().coerceAtLeast(0)
        seekBar.progress = currentPosition.toInt().coerceAtMost(seekBar.max)
        txtPosition.text = formatMillis(currentPosition)
    }

    private fun tickClock() {
        val now = Date()
        val timeText = clockFormat.format(now)
        txtClock.text = timeText
        txtBigClock.text = timeText
        txtBigDate.text = dateFormat.format(now)
    }

    private fun applyBackgroundBlur() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val blur = android.graphics.RenderEffect.createBlurEffect(
                60f, 60f, android.graphics.Shader.TileMode.CLAMP
            )
            imgBackground.setRenderEffect(blur)
        }
    }

    private fun formatMillis(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
