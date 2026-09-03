package com.example.nowplaying

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 이 서비스 자체는 알림 내용을 사용하지 않는다.
 * 안드로이드에서 다른 앱의 MediaSession(재생 정보)에 접근하려면
 * "알림 접근 권한"이 허용된 NotificationListenerService 컴포넌트가 있어야 하므로,
 * 그 조건을 충족시키기 위한 최소 구현이다.
 */
class MediaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 사용하지 않음
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 사용하지 않음
    }
}
