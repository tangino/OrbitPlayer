package com.orbit.music.ui.components.auth

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbit.music.data.online.auth.PlatformAccountManager
import com.orbit.music.data.online.auth.model.PlatformAccount
import com.orbit.music.data.online.auth.model.QrCodeData
import com.orbit.music.data.online.auth.model.QrStatus
import com.orbit.music.data.online.auth.util.QrCodeGenerator
import com.orbit.music.data.online.model.OnlinePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 平台登录弹窗：支持二维码扫码登录与手动填入 Cookie 两种方式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformLoginDialog(
    platform: OnlinePlatform,
    onDismiss: () -> Unit,
    onLoginSuccess: (PlatformAccount) -> Unit
) {
    val context = LocalContext.current
    val accountManager = remember { PlatformAccountManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // 默认展示扫码登录 (0: 扫码登录, 1: 填入 Cookie)
    var selectedTab by remember { mutableIntStateOf(0) }

    // ====== 扫码登录相关状态 ======
    var qrCodeData by remember { mutableStateOf<QrCodeData?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isQrLoading by remember { mutableStateOf(false) }
    var qrStatus by remember { mutableStateOf(QrStatus.WAITING) }
    var qrStatusMessage by remember { mutableStateOf("正在获取二维码...") }
    var qrErrorMessage by remember { mutableStateOf<String?>(null) }
    var qrRefreshTrigger by remember { mutableIntStateOf(0) }

    // 获取并解码二维码
    fun fetchQrCode() {
        scope.launch {
            isQrLoading = true
            qrErrorMessage = null
            qrStatus = QrStatus.WAITING
            qrStatusMessage = "正在获取二维码..."
            qrBitmap = null
            try {
                val data = accountManager.getQrCode(platform)
                qrCodeData = data

                var bmp: Bitmap? = null
                // 1. 若直接返回了二进制图片数据
                if (data.qrImageBytes != null && data.qrImageBytes.isNotEmpty()) {
                    bmp = BitmapFactory.decodeByteArray(data.qrImageBytes, 0, data.qrImageBytes.size)
                }
                // 2. 若有二维码跳转 URL，生成二维码位图
                if (bmp == null && !data.qrUrl.isNullOrBlank()) {
                    bmp = QrCodeGenerator.createQrBitmap(data.qrUrl, 512)
                }
                qrBitmap = bmp

                qrStatusMessage = if (platform == OnlinePlatform.KUGOU) {
                    "请使用酷狗音乐手机 App 扫码登录"
                } else {
                    "请使用 QQ 音乐或微信扫码登录"
                }
            } catch (e: Exception) {
                qrErrorMessage = "获取二维码失败: ${e.message}"
                qrStatus = QrStatus.ERROR
            } finally {
                isQrLoading = false
            }
        }
    }

    // 每次切换到扫码 Tab 或触发刷新时拉取二维码
    LaunchedEffect(platform, qrRefreshTrigger) {
        if (selectedTab == 0) {
            fetchQrCode()
        }
    }

    // 轮询检查扫码登录状态
    LaunchedEffect(qrCodeData?.key) {
        val key = qrCodeData?.key
        if (key.isNullOrBlank()) return@LaunchedEffect

        var attempts = 0
        val maxAttempts = 120 // 约 4 分钟
        while (isActive && attempts < maxAttempts && qrStatus != QrStatus.SUCCESS && qrStatus != QrStatus.EXPIRED) {
            delay(2000)
            attempts++
            try {
                val checkResult = accountManager.checkQrStatus(
                    platform = platform,
                    key = key,
                    extra = if (platform == OnlinePlatform.QQ) mapOf("qrsig" to key) else emptyMap()
                )
                qrStatus = checkResult.status
                when (checkResult.status) {
                    QrStatus.WAITING -> {
                        qrStatusMessage = if (platform == OnlinePlatform.KUGOU) {
                            "请使用酷狗音乐手机 App 扫码登录"
                        } else {
                            "请使用 QQ 音乐或微信扫码登录"
                        }
                    }
                    QrStatus.SCANNED -> {
                        qrStatusMessage = "二维码已扫描，请在手机上点击确认"
                    }
                    QrStatus.SUCCESS -> {
                        qrStatusMessage = "授权成功，登录中..."
                        val account = checkResult.account
                        if (account != null) {
                            Toast.makeText(context, "登录成功: ${account.nickname}", Toast.LENGTH_SHORT).show()
                            delay(600)
                            onLoginSuccess(account)
                            onDismiss()
                        }
                        break
                    }
                    QrStatus.EXPIRED -> {
                        qrStatusMessage = "二维码已失效，请点击刷新"
                        break
                    }
                    QrStatus.ERROR -> {
                        if (checkResult.message.isNotBlank()) {
                            qrStatusMessage = checkResult.message
                        }
                    }
                }
            } catch (_: Exception) {
                // 忽略单次网络抖动，继续轮询
            }
        }
    }

    // ====== 手动输入 Cookie 相关状态与逻辑 ======
    var rawInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString().trim()
                    if (text.isNotBlank()) {
                        rawInput = text
                        errorMessage = null
                    } else {
                        Toast.makeText(context, "剪贴板内容为空", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "读取剪贴板失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun decodeUnicodeEscape(input: String): String {
        return try {
            val unicodeRegex = Regex("%u([0-9a-fA-F]{4})")
            val replaced = unicodeRegex.replace(input) { m ->
                val hex = m.groupValues[1]
                hex.toInt(16).toChar().toString()
            }
            java.net.URLDecoder.decode(replaced, "UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    fun parseCookieMap(input: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val text = input.trim()

        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                val jsonArray = org.json.JSONArray(text)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name", obj.optString("key", ""))
                    val value = obj.optString("value", "")
                    if (name.isNotBlank()) {
                        map[name] = value
                    }
                }
                if (map.isNotEmpty()) return map
            } catch (_: Exception) {}
        } else if (text.startsWith("{") && text.endsWith("}")) {
            try {
                val jsonObj = org.json.JSONObject(text)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObj.optString(key, "")
                }
                if (map.isNotEmpty()) return map
            } catch (_: Exception) {}
        }

        val lines = text.split("\n", ";", "&")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("=")) {
                val equalIdx = trimmed.indexOf('=')
                val key = trimmed.substring(0, equalIdx).trim()
                val value = trimmed.substring(equalIdx + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            } else if (trimmed.contains("\t")) {
                val parts = trimmed.split("\t")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    if (key.isNotEmpty()) {
                        map[key] = value
                    }
                }
            } else if (trimmed.contains(":")) {
                val colonIdx = trimmed.indexOf(':')
                val key = trimmed.substring(0, colonIdx).trim()
                val value = trimmed.substring(colonIdx + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }

        val compoundKeys = listOf("KuGou", "KuGouPercent", "kugou", "kugou_info")
        for (ck in compoundKeys) {
            val encodedVal = map[ck]
            if (!encodedVal.isNullOrBlank()) {
                try {
                    val decoded = java.net.URLDecoder.decode(encodedVal, "UTF-8")
                    val subPairs = decoded.split("&", ";")
                    for (sub in subPairs) {
                        val idx = sub.indexOf('=')
                        if (idx > 0) {
                            val k = sub.substring(0, idx).trim()
                            val v = sub.substring(idx + 1).trim()
                            if (k.isNotEmpty() && !map.containsKey(k)) {
                                map[k] = v
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return map
    }

    fun doValidateAndLogin() {
        if (rawInput.isBlank()) {
            errorMessage = "请输入或粘贴 Cookie / Token 内容"
            return
        }

        scope.launch {
            isValidating = true
            errorMessage = null

            try {
                val parsedCookies = parseCookieMap(rawInput)
                val cookiesMap = parsedCookies.toMutableMap()
                val trimmedInput = rawInput.trim()

                val keyPattern = Regex("(Q_H_L_[A-Za-z0-9_-]+)")
                val keyMatch = keyPattern.find(trimmedInput)
                if (keyMatch != null) {
                    val extractedKey = keyMatch.value
                    cookiesMap["qm_keyst"] = extractedKey
                    cookiesMap["qqmusic_key"] = extractedKey
                    cookiesMap["psrf_musickey_id"] = extractedKey
                    cookiesMap["authst"] = extractedKey
                } else if (trimmedInput.startsWith("Q_H_L_") || (trimmedInput.length >= 32 && !trimmedInput.contains("=") && !trimmedInput.contains("\t") && !trimmedInput.contains("&"))) {
                    cookiesMap["qm_keyst"] = trimmedInput
                    cookiesMap["qqmusic_key"] = trimmedInput
                    cookiesMap["psrf_musickey_id"] = trimmedInput
                    cookiesMap["authst"] = trimmedInput
                }

                val uinPattern = Regex("(?:uin|p_uin|login_uin|userid|qq|用户|账号)[=:：\\s]*([0-9]{5,12})", RegexOption.IGNORE_CASE)
                val uinMatch = uinPattern.find(trimmedInput)
                if (uinMatch != null) {
                    val extractedUin = uinMatch.groupValues[1]
                    cookiesMap["uin"] = extractedUin
                    cookiesMap["login_uin"] = extractedUin
                }

                var userId = ""
                var nickname = ""

                when (platform) {
                    OnlinePlatform.QQ -> {
                        userId = cookiesMap["uin"] ?: cookiesMap["p_uin"] ?: cookiesMap["login_uin"] ?: ""
                        userId = userId.replace("^o0*".toRegex(), "")

                        if (userId.isBlank()) {
                            val digitsPattern = Regex("\\b([1-9][0-9]{4,10})\\b")
                            val digitsMatch = digitsPattern.find(trimmedInput)
                            if (digitsMatch != null) {
                                userId = digitsMatch.value
                                cookiesMap["uin"] = userId
                            }
                        }

                        if (userId.isBlank() && !cookiesMap.containsKey("qm_keyst") && !cookiesMap.containsKey("qqmusic_key") && !cookiesMap.containsKey("p_skey")) {
                            errorMessage = "未在输入中检测到 uin 或 qm_keyst 凭据，请确认粘贴了完整 Cookie"
                            isValidating = false
                            return@launch
                        }

                        if (userId.isBlank()) {
                            userId = "748264063"
                        }
                        nickname = "QQ 用户 $userId"
                    }

                    OnlinePlatform.KUGOU -> {
                        var token = cookiesMap["token"] ?: cookiesMap["t"] ?: cookiesMap["kg_token"] ?: cookiesMap["login_token"] ?: cookiesMap["user_token"] ?: cookiesMap["KuGou"] ?: ""
                        userId = cookiesMap["KugooID"] ?: cookiesMap["KugouID"] ?: cookiesMap["KugouId"] ?: cookiesMap["userid"] ?: cookiesMap["kg_mid"] ?: cookiesMap["uid"] ?: cookiesMap["mid"] ?: ""

                        if (token.isBlank()) {
                            val tokenRegex = Regex("""(?:token|kg_token|login_token|user_token|\bt\b)["']?\s*[:=]\s*["']?([a-zA-Z0-9_\-]{16,})["']?""", RegexOption.IGNORE_CASE)
                            val match = tokenRegex.find(trimmedInput)
                            if (match != null) {
                                token = match.groupValues[1]
                                cookiesMap["token"] = token
                            }
                        }

                        if (userId.isBlank()) {
                            val userRegex = Regex("""(?:userid|KugooID|KugouID|KugouId|kg_mid|uid|mid)["']?\s*[:=]\s*["']?(\d{4,15})["']?""", RegexOption.IGNORE_CASE)
                            val match = userRegex.find(trimmedInput)
                            if (match != null) {
                                userId = match.groupValues[1]
                                cookiesMap["userid"] = userId
                            }
                        }

                        if (token.isBlank() && trimmedInput.length in 16..64 && !trimmedInput.contains("=") && !trimmedInput.contains(";") && !trimmedInput.contains("&") && !trimmedInput.contains(" ")) {
                            token = trimmedInput
                            cookiesMap["token"] = token
                        }

                        if (token.isBlank() && userId.isBlank()) {
                            errorMessage = "未在输入中检测到 token 或 userid 凭据"
                            isValidating = false
                            return@launch
                        }

                        if (userId.isBlank()) userId = "0"
                        if (token.isNotBlank()) {
                            cookiesMap["token"] = token
                            cookiesMap["t"] = token
                        }
                        if (userId.isNotBlank() && userId != "0") {
                            cookiesMap["userid"] = userId
                            cookiesMap["KugouID"] = userId
                            cookiesMap["KugooID"] = userId
                        }

                        val rawNick = cookiesMap["NickName"] ?: cookiesMap["UserName"] ?: cookiesMap["nickname"] ?: ""
                        val decodedNick = if (rawNick.isNotBlank()) decodeUnicodeEscape(rawNick).trim() else ""
                        nickname = if (decodedNick.isNotBlank()) decodedNick else "酷狗用户_$userId"
                    }

                    else -> {
                        userId = "User_${System.currentTimeMillis() % 10000}"
                        nickname = "${platform.displayName} 用户"
                    }
                }

                val baseAccount = PlatformAccount(
                    platform = platform,
                    userId = userId,
                    nickname = nickname,
                    avatarUrl = if (platform == OnlinePlatform.QQ && userId.matches(Regex("^\\d+$"))) "https://q1.qlogo.cn/g?b=qq&nk=$userId&s=100" else "",
                    cookies = cookiesMap,
                    tokens = if (platform == OnlinePlatform.KUGOU) {
                        val tok = cookiesMap["token"] ?: cookiesMap["t"] ?: ""
                        mapOf("token" to tok, "t" to tok, "userid" to userId)
                    } else mapOf("uin" to userId),
                    updatedAt = System.currentTimeMillis()
                )

                val finalAccount = try {
                    withContext(Dispatchers.IO) {
                        accountManager.getAuthService(platform)?.refreshUserInfo(baseAccount) ?: baseAccount
                    }
                } catch (_: Exception) {
                    baseAccount
                }

                accountManager.saveAccount(finalAccount)
                Toast.makeText(context, "登录成功: ${finalAccount.nickname}", Toast.LENGTH_SHORT).show()
                onLoginSuccess(finalAccount)
                onDismiss()
            } catch (e: Exception) {
                errorMessage = "校验异常: ${e.message}"
            } finally {
                isValidating = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.QrCodeScanner else Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${platform.displayName} 账号登录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab 切换按钮组 (扫码登录 / 填入 Cookie)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        // 扫码登录选项卡
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable {
                                    selectedTab = 0
                                    if (qrCodeData == null) {
                                        qrRefreshTrigger++
                                    }
                                },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (selectedTab == 0) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "扫码登录",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 填入 Cookie 选项卡
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { selectedTab = 1 },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (selectedTab == 1) 2.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "填入 Cookie",
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ====== 选项卡 1：扫码登录视图 ======
                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 二维码展示容器 (必须保持纯白内衬以便手机镜头识别)
                        Box(
                            modifier = Modifier
                                .size(230.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isQrLoading) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "获取二维码中...",
                                        fontSize = 13.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            } else if (qrErrorMessage != null && qrBitmap == null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = qrErrorMessage ?: "加载失败",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    TextButton(onClick = { qrRefreshTrigger++ }) {
                                        Text("点击重试", fontSize = 13.sp)
                                    }
                                }
                            } else if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "登录二维码",
                                    modifier = Modifier
                                        .size(206.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                // 二维码过期半透明遮罩
                                if (qrStatus == QrStatus.EXPIRED) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.72f))
                                            .clickable { qrRefreshTrigger++ },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "刷新",
                                                tint = Color.White,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "二维码已过期\n点击重新获取",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 状态提示与引导
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (qrStatus) {
                                QrStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                QrStatus.SCANNED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                QrStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                when (qrStatus) {
                                    QrStatus.WAITING -> {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    QrStatus.SCANNED -> {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    QrStatus.SUCCESS -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    QrStatus.EXPIRED -> {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    QrStatus.ERROR -> {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = qrStatusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = when (qrStatus) {
                                        QrStatus.EXPIRED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 底部操作区
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("取消")
                            }

                            Button(
                                onClick = { qrRefreshTrigger++ },
                                enabled = !isQrLoading,
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("刷新二维码")
                            }
                        }
                    }
                }

                // ====== 选项卡 2：手动填入 Cookie 视图 ======
                if (selectedTab == 1) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 操作指引说明
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 如何获取 ${platform.displayName} Cookie：",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (platform) {
                                        OnlinePlatform.QQ -> "电脑或手机浏览器打开 y.qq.com 登录后，复制 Cookie（需包含 uin 与 qm_keyst 或 p_skey）。"
                                        OnlinePlatform.KUGOU -> "打开 kugou.com 登录后，复制完整 Cookie 或 KuGou 串（系统会自动识别 KugooID、t 令牌与昵称）。"
                                        else -> "在浏览器登录对应平台后，复制开发者工具中的 Cookie 字符串。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Cookie 输入框
                        OutlinedTextField(
                            value = rawInput,
                            onValueChange = {
                                rawInput = it
                                errorMessage = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 130.dp, max = 220.dp),
                            placeholder = {
                                Text(
                                    text = if (platform == OnlinePlatform.QQ) "例如：uin=12345678; qm_keyst=Q_H_L_...; p_skey=..." else "例如：KugooID=2600762794&t=95d0... 或 直接粘贴完整 Cookie",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (rawInput.isNotEmpty()) {
                                    IconButton(onClick = { rawInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 快捷工具栏（一键粘贴）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { pasteFromClipboard() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("从剪贴板粘贴", fontSize = 13.sp)
                            }

                            if (rawInput.isNotBlank()) {
                                Text(
                                    text = "已输入 ${rawInput.length} 字符",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 错误提示
                        AnimatedVisibility(visible = errorMessage != null) {
                            errorMessage?.let { msg ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 底部操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("取消")
                            }

                            Button(
                                onClick = { doValidateAndLogin() },
                                enabled = !isValidating && rawInput.isNotBlank(),
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isValidating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("校验中...")
                                } else {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("保存并登录")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
