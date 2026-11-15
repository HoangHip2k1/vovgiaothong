package vn.vovgt.plus

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.exoplayer.offline.Download
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Cyan = Color(0xFF11C5DD)
private val Deep = Color(0xFF0B1630)
private val Royal = Color(0xFF157CF1)
private val Pale = Color(0xFFE7F8FB)
private val Background = Color(0xFFF5F8FC)
private val TextDark = Color(0xFF192944)
private val TextMuted = Color(0xFF69798C)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val playbackController = mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        setContent { VovPlusApp(playbackController.value) }
    }
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, AudioService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try { playbackController.value = future.get() }
            catch (_: Exception) { playbackController.value = null }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onStop() {
        playbackController.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}

private enum class Tab(val label: String) { SCHEDULE("Lịch phát"), LIVE("Trực tiếp"), HISTORY("Đã tải"), ACCOUNT("Tài khoản") }
private data class QueuedMedia(val url: String, val title: String)

@Composable
private fun VovPlusApp(player: MediaController?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vov_settings", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var confirmedSession by remember { mutableStateOf(prefs.getBoolean("session_confirmed", false)) }
    var selectedTab by remember { mutableStateOf(Tab.SCHEDULE) }
    var pending by remember { mutableStateOf<QueuedMedia?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val report: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    val download: (String, String) -> Unit = { url, title ->
        when {
            !VovData.validHls(url) -> report("Đường dẫn không hợp lệ. Cần HTTPS .m3u8")
            VovData.isLive(url) -> report("Kênh trực tiếp chỉ hỗ trợ nghe. Hãy tải chương trình phát lại.")
            !confirmedSession -> {
                pending = QueuedMedia(url, title)
                selectedTab = Tab.ACCOUNT
                report("Hãy đăng nhập VOV Media trước khi tải.")
            }
            else -> try {
                OfflineStore.enqueue(context, url, title)
                selectedTab = Tab.HISTORY
                report("Đã thêm vào danh sách tải. Bạn có thể thoát màn hình ứng dụng.")
            } catch (ex: Exception) { report(ex.message ?: "Không thể bắt đầu tải") }
        }
    }
    val listen: (String, String) -> Unit = { url, title ->
        if (!VovData.validHls(url)) report("Link phát không hợp lệ")
        else if (player == null) report("Đang kết nối bộ phát âm thanh, thử lại sau.")
        else try {
            val media = MediaItem.Builder().setUri(url).setMediaId(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("VOV Giao thông+").build())
                .build()
            player.setMediaItem(media)
            player.prepare()
            player.play()
            report("Đang mở: $title")
        } catch (ex: Exception) { report(ex.message ?: "Không thể phát luồng âm thanh") }
    }
    val colors = if (dark) darkColorScheme(
        primary = Cyan, onPrimary = Deep, background = Color(0xFF101827),
        surface = Color(0xFF1A2638), onSurface = Color(0xFFEAF1FC),
        onBackground = Color(0xFFEAF1FC), secondary = Cyan
    ) else lightColorScheme(
        primary = Royal, secondary = Cyan, background = Background,
        surface = Color.White, onSurface = TextDark, onBackground = TextDark
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Deep)
                        .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).background(Cyan, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = Deep, modifier = Modifier.size(29.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("VOV GIAO THÔNG+", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
