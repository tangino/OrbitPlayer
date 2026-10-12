package com.orbit.music.ui.components.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.orbit.music.data.online.auth.PlatformAccountManager
import com.orbit.music.data.online.auth.model.PlatformAccount
import com.orbit.music.data.online.auth.service.QQMusicAuthService
import com.orbit.music.data.online.model.OnlinePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "QQMusicWebQrLogin"
private const val QQ_MUSIC_URL = "https://y.qq.com/"
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * QQ 音乐内嵌网页扫码登录组件（全面打通登录成功跳转感知）
 *
 * 核心解决“手机扫码确认后内嵌网页不跳转、无反应”问题：
 * 1. 深度聚合相关子域（y.qq.com, ptlogin2.qq.com, graph.qq.com 等）的所有 Cookie，解除单一域名取不到跨域凭证的局限；
 * 2. 注入 JS 桥接，实时监听 iframe postMessage 跨域登录消息及 DOM 登录态变化；
 * 3. 实时拦截 login_jump、wx_redirect.html、musicu.fcg 等授权完成特征 URL；
 * 4. 判定只要具备有效 uin + p_skey 或 qm_keyst / musickey，立即秒级完成登录闭环并回调跳转。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QQMusicWebQrLoginContent(
    modifier: Modifier = Modifier,
    onLoginSuccess: (PlatformAccount) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountManager = remember { PlatformAccountManager.getInstance(context) }
    val authService = remember {
        (accountManager.getAuthService(OnlinePlatform.QQ) as? QQMusicAuthService) ?: QQMusicAuthService()
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoginSuccessHandled by remember { mutableStateOf(false) }
    var loginStatusText by remember { mutableStateOf("正在载入 QQ 音乐安全登录界面...") }

    // 聚合腾讯所有子域名的 Cookie
    fun collectAllRelevantCookies(): String {
        val cm = CookieManager.getInstance()
        val domains = listOf(
            "https://y.qq.com",
            "https://y.qq.com/",
            "https://u.y.qq.com",
            "https://c.y.qq.com",
            "https://xui.ptlogin2.qq.com",
            "https://ssl.ptlogin2.qq.com",
            "https://ptlogin2.qq.com",
            "https://graph.qq.com",
            "https://open.weixin.qq.com",
            "https://qq.com"
        )
        val cookieMap = mutableMapOf<String, String>()
        for (domain in domains) {
            val str = cm.getCookie(domain) ?: continue
            for (part in str.split(";")) {
                val kv = part.trim().split("=")
                if (kv.size >= 2 && kv[0].isNotBlank()) {
                    cookieMap[kv[0]] = kv.drop(1).joinToString("=").trim()
                }
            }
        }
        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    // 检查并提取登录成功凭据
    fun checkAndProcessCookies(forceReason: String = "") {
        if (isLoginSuccessHandled) return

        val allCookies = collectAllRelevantCookies()
        if (allCookies.isBlank()) return

        // 提取主要特征
        val hasKey = allCookies.contains("qm_keyst=") ||
                allCookies.contains("qqmusic_key=") ||
                allCookies.contains("psrf_musickey_id=") ||
                allCookies.contains("musickey=") ||
                allCookies.contains("p_skey=") ||
                allCookies.contains("skey=")

        // 提取有效 uin (排查 uin=o0 或空)
        val hasValidUin = (allCookies.contains("uin=") && !allCookies.contains("uin=o0;") && !allCookies.endsWith("uin=o0")) ||
                allCookies.contains("p_uin=") ||
                allCookies.contains("musicid=") ||
                allCookies.contains("wxopenid=")

        // 只要具备账号标识和任一密钥凭证，即可确认授权成功
        if (hasKey && hasValidUin) {
            isLoginSuccessHandled = true
            loginStatusText = "登录授权成功，正在获取账户信息..."
            Log.i(TAG, "成功捕获有效凭据(触发原因: $forceReason)，完整Cookie: $allCookies")

            scope.launch {
                try {
                    val account = authService.parseAccountFromCookies(allCookies)
                    if (account != null) {
                        accountManager.saveAccount(account)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "QQ 音乐登录成功: ${account.nickname}", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(account)
                        }
                    } else {
                        Log.w(TAG, "凭据解析失败，继续等待下一轮刷新")
                        isLoginSuccessHandled = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析账号异常: ${e.message}", e)
                    isLoginSuccessHandled = false
                }
            }
        }
    }

    // 注入 JavaScript，同时兼顾弹窗居中缩放与登录事件监听
    fun injectStyleAndListeners(view: WebView?) {
        if (view == null) return
        val js = """
            javascript:(function() {
                try {
                    // 1. 注入自适应定位与背景隐藏样式
                    var existingStyle = document.getElementById('orbit_qq_custom_style');
                    if (!existingStyle) {
                        var style = document.createElement('style');
                        style.id = 'orbit_qq_custom_style';
                        style.innerHTML = `
                            /* 彻底隐藏页面非弹窗元素及遮罩 */
                            .mod_header, .footer, .btn_bottom_top, .btn_bottom_report, 
                            .btn_bottom_feedback, .btn_bottom_player, .popup__mask,
                            #app > div:not(.mod_popup):not([class*="popup"]):not([class*="dialog"]) {
                                display: none !important;
                                visibility: hidden !important;
                                opacity: 0 !important;
                            }
                            body, html {
                                background: #FFFFFF !important;
                                overflow: hidden !important;
                                margin: 0 !important;
                                padding: 0 !important;
                                width: 100% !important;
                                height: 100% !important;
                            }
                            /* 弹窗基准样式 */
                            .mod_popup, .popup_login, [class*="popup_login"], [class*="mod_popup"] {
                                position: fixed !important;
                                top: 50% !important;
                                left: 50% !important;
                                margin: 0 !important;
                                box-shadow: none !important;
                                z-index: 999999 !important;
                                transform-origin: center center !important;
                            }
                        `;
                        document.head.appendChild(style);
                    }

                    // 2. 动态自适应缩放计算
                    function fitPopup() {
                        var popup = document.querySelector('.mod_popup, .popup_login, [class*="popup_login"], [class*="mod_popup"]');
                        if (!popup) return;

                        var pw = popup.offsetWidth || 700;
                        var ph = popup.offsetHeight || 470;
                        var ww = window.innerWidth || document.documentElement.clientWidth;
                        var wh = window.innerHeight || document.documentElement.clientHeight;

                        var scaleX = (ww - 16) / pw;
                        var scaleY = (wh - 16) / ph;
                        var scale = Math.min(scaleX, scaleY, 1.0);
                        if (scale < 0.35) scale = 0.35;

                        popup.style.position = 'fixed';
                        popup.style.top = '50%';
                        popup.style.left = '50%';
                        popup.style.transform = 'translate(-50%, -50%) scale(' + scale.toFixed(3) + ')';
                    }

                    // 3. 查找并点击登录按钮触发弹窗
                    function clickLogin() {
                        var loginBtn = document.querySelector('.top_login__link');
                        if (loginBtn) {
                            loginBtn.click();
                            fitPopup();
                            return true;
                        }
                        var elements = document.querySelectorAll('a, button, span');
                        for (var i = 0; i < elements.length; i++) {
                            if (elements[i].innerText && elements[i].innerText.trim() === '登录') {
                                elements[i].click();
                                fitPopup();
                                return true;
                            }
                        }
                        return false;
                    }

                    clickLogin();
                    fitPopup();

                    var retryCount = 0;
                    var interval = setInterval(function() {
                        retryCount++;
                        fitPopup();
                        if (clickLogin() || retryCount >= 10) {
                            if (retryCount >= 10) clearInterval(interval);
                        }
                    }, 400);

                    window.addEventListener('resize', fitPopup);

                    // 4. 监听跨域 postMessage（iframe 授权成功通知）
                    if (!window._orbit_msg_listening) {
                        window._orbit_msg_listening = true;
                        window.addEventListener('message', function(event) {
                            try {
                                var d = event.data;
                                var str = typeof d === 'string' ? d : JSON.stringify(d);
                                if (str && (str.indexOf('login') !== -1 || str.indexOf('uin') !== -1 || str.indexOf('ptui') !== -1 || str.indexOf('code') !== -1)) {
                                    if (window.OrbitLoginBridge) {
                                        window.OrbitLoginBridge.onLoginEvent('postMessage', str);
                                    }
                                }
                            } catch(e) {}
                        });
                    }

                    // 5. 监听 DOM 树登录态演变（当登录成功时，主页右上角“登录”按钮会变为头像或昵称）
                    if (!window._orbit_dom_monitoring) {
                        window._orbit_dom_monitoring = true;
                        setInterval(function() {
                            try {
                                var userLink = document.querySelector('.mod_top_user, .top_user__link, [class*="top_user"], [class*="user_name"]');
                                var loginBtn = document.querySelector('.top_login__link');
                                if (userLink || (!loginBtn && document.cookie.indexOf('uin=') !== -1)) {
                                    if (window.OrbitLoginBridge) {
                                        window.OrbitLoginBridge.onLoginEvent('domChange', 'user_module_detected');
                                    }
                                }
                            } catch(e) {}
                        }, 800);
                    }
                } catch(e) {
                    console.error('Orbit inject error:', e);
                }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    // 协程高频轮询 Cookie 变更
    LaunchedEffect(Unit) {
        while (isActive && !isLoginSuccessHandled) {
            delay(800)
            checkAndProcessCookies(forceReason = "periodic_poll")
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 精致提示小条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isLoginSuccessHandled) Icons.Default.Check else Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isLoginSuccessHandled) {
                    loginStatusText
                } else {
                    "支持手机 QQ 或 微信 扫码授权"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (isLoginSuccessHandled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 网页弹窗展示容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            textZoom = 100
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = DESKTOP_USER_AGENT
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            setSupportMultipleWindows(false)
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // 注入原生 JS 桥梁
                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onLoginEvent(eventType: String, extraInfo: String) {
                                Log.i(TAG, "收到网页通知登录事件: $eventType, 详情: $extraInfo")
                                scope.launch(Dispatchers.Main) {
                                    checkAndProcessCookies(forceReason = "JSBridge_$eventType")
                                }
                            }
                        }, "OrbitLoginBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                loadError = null
                                checkAndProcessCookies(forceReason = "onPageStarted")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                injectStyleAndListeners(view)
                                checkAndProcessCookies(forceReason = "onPageFinished")
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                if (url != null) {
                                    // 拦截到授权成功重定向或换取凭据的核心接口
                                    if (url.contains("login_jump") || 
                                        url.contains("wx_redirect.html") || 
                                        url.contains("musicu.fcg") || 
                                        url.contains("fcg_login") ||
                                        url.contains("ptqrlogin") && url.contains("action=0-0-")
                                    ) {
                                        checkAndProcessCookies(forceReason = "onLoadResource_${url.take(45)}")
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val jumpUrl = request?.url?.toString() ?: ""
                                Log.d(TAG, "页面尝试重定向: $jumpUrl")

                                // 若检测到微信扫码重定向携带了授权 code，协助顶级页面加载
                                if (jumpUrl.contains("wx_redirect.html") && jumpUrl.contains("code=")) {
                                    view?.loadUrl(jumpUrl)
                                    checkAndProcessCookies(forceReason = "wx_redirect_detected")
                                    return true
                                }

                                checkAndProcessCookies(forceReason = "shouldOverrideUrlLoading")
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                    loadError = "加载页面失败，请检查网络"
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                injectStyleAndListeners(view)
                                checkAndProcessCookies(forceReason = "doUpdateVisitedHistory")
                            }
                        }

                        webViewInstance = this
                        loadUrl(QQ_MUSIC_URL)
                    }
                },
                update = {
                    webViewInstance = it
                }
            )

            // 加载指示遮罩
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "正在载入 QQ 音乐安全登录界面...",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            // 错误重试遮罩
            if (loadError != null && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = loadError ?: "加载异常",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                loadError = null
                                webViewInstance?.reload()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重新加载", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 底部紧凑控制按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text("关闭", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    isLoading = true
                    loadError = null
                    webViewInstance?.reload()
                },
                enabled = !isLoading,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新网页", fontSize = 13.sp)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
}
