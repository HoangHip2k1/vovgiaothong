package vn.vovgt.plus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler

class VovApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        OfflineStore.init(this)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel("vov_status", "Kết quả tải xuống", NotificationManager.IMPORTANCE_DEFAULT)
        )
        OfflineStore.manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                val title = when (download.state) {
                    Download.STATE_COMPLETED -> "Tải hoàn tất"
                    Download.STATE_FAILED -> "Không thể tải chương trình"
                    else -> return
                }
                val text = if (download.state == Download.STATE_FAILED)
                    "${OfflineStore.title(download)} · Kiểm tra đăng nhập hoặc kết nối" else OfflineStore.title(download)
                val intent = PendingIntent.getActivity(
                    this@VovApplication, 0, Intent(this@VovApplication, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val notification = Notification.Builder(this@VovApplication, "vov_status")
                    .setSmallIcon(R.drawable.ic_stat_radio)
                    .setContentTitle(title).setContentText(text).setContentIntent(intent)
                    .setAutoCancel(true).build()
                notifications.notify(download.request.id.hashCode() and 0x7fffffff, notification)
            }
        })
    }
}

@OptIn(UnstableApi::class)
class VovDownloadService : DownloadService(102, 1000L, "vov_downloads", R.string.download_channel, R.string.download_channel_description) {
    override fun getDownloadManager(): DownloadManager = OfflineStore.manager

    override fun getScheduler(): Scheduler? = PlatformScheduler(this, 25001)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val active = downloads.filter { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING }
        val total = active.count()
        val amounts = active.mapNotNull { it.percentDownloaded.takeIf { pct -> pct >= 0f } }
        val progress = if (amounts.isEmpty()) 0 else amounts.average().toInt().coerceIn(0, 100)
        val message = when {
            notMetRequirements != 0 -> "Đang chờ kết nối mạng…"
            total > 1 -> "Đang tải $total chương trình · $progress%"
            total == 1 -> OfflineStore.title(active.first())
            else -> "Đang chuẩn bị tải…"
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, "vov_downloads")
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle("VOV Giao thông+ · Đang tải")
            .setContentText(message)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, amounts.isEmpty())
            .build()
    }
}

@OptIn(UnstableApi::class)
class AudioService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OfflineStore.playbackFactory()))
            .build()
        session = MediaSession.Builder(this, player).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
