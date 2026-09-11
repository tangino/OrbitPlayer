package com.antigravity.equalizer.ui.components

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.antigravity.equalizer.R
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.provider.AudioCoverProvider
import com.antigravity.equalizer.ui.theme.OrbitTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.floor

/**
 * 经典 Mac OS X (Finder / iTunes) 风格的 3D Cover Flow 视图与联动列表
 * 1. 顶部 3D 封面流：具备 Y 轴立体旋转倾斜、左右多层景深交叠、真实垂直镜面倒影
 * 2. 居中歌曲信息与胶囊滑块：支持拖拽快速穿梭
 * 3. 底部联动列表：与 Cover Flow 双向无缝联动同步（滑轮播滚列表、点列表滑轮播、高亮选中）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverFlowLayout(
    songs: List<Song>,
    currentPlayingSongId: Long?,
    isPlaying: Boolean,
    coverVersion: Long,
    onSongClick: (Song, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 98.dp,
    isInertiaEnabled: Boolean? = null,
    onToggleInertia: ((Boolean) -> Unit)? = null,
    locateTrigger: Long = 0L
) {
    if (songs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.empty_songs_title),
                color = OrbitTheme.colors.textSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp

    // 确定初始展示歌曲位置：优先居中当前播放的歌曲，否则从第 0 首开始
    val initialIndex = remember(songs, currentPlayingSongId) {
        val found = songs.indexOfFirst { it.id == currentPlayingSongId }
        if (found >= 0) found else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, songs.size - 1),
        pageCount = { songs.size }
    )

    val listState = rememberLazyListState()

    // 记录是否是列表主动触发滑动，避免双向联动死循环
    var isUserDraggingList by remember { mutableStateOf(false) }

    // 持久化存储并读取 Cover Flow 滑动惯性配置
    val prefs = remember(context) {
        context.getSharedPreferences("music_library_ui_prefs", Context.MODE_PRIVATE)
    }
    var localInertiaEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("key_cover_flow_inertia", true))
    }
    val effectiveInertia = isInertiaEnabled ?: localInertiaEnabled

    val toggleInertia: (Boolean) -> Unit = { enabled ->
        localInertiaEnabled = enabled
        prefs.edit().putBoolean("key_cover_flow_inertia", enabled).apply()
        onToggleInertia?.invoke(enabled)
        Toast.makeText(
            context,
            if (enabled) context.getString(R.string.cover_flow_inertia_enabled_toast)
            else context.getString(R.string.cover_flow_inertia_disabled_toast),
            Toast.LENGTH_SHORT
        ).show()
    }

    // 封面长按选项菜单开关状态
    var showCoverOptions by remember { mutableStateOf(false) }

    // 针对 Cover Flow 专门调优的高动量超低摩擦衰减曲线与丝滑微弹簧吸附（行云流水般的机械飞轮质感）
    val smoothDecay = exponentialDecay<Float>(
        frictionMultiplier = 0.32f, // 黄金阻尼，兼具长距滑行与顺滑跟手
        absVelocityThreshold = 0.08f
    )
    val smoothSnap = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 320f // 响应灵敏、吸附自然的顺滑微弹簧，消除迟滞顿挫感
    )
    val defaultDecay = rememberSplineBasedDecay<Float>()
    val defaultSnap = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow
    )

    // 根据惯性开关状态配置 FlingBehavior (开启时允许滑行多页物理阻尼，关闭时单页精准吸附)
    val flingBehavior = if (effectiveInertia) {
        PagerDefaults.flingBehavior(
            pagerState,
            PagerSnapDistance.atMost(40),
            smoothSnap,
            smoothDecay,
            smoothSnap
        )
    } else {
        PagerDefaults.flingBehavior(
            pagerState,
            PagerSnapDistance.atMost(1),
            defaultSnap,
            defaultDecay,
            defaultSnap
        )
    }

    // Cover Flow 翻页停稳时，下方列表同步平滑滚动至该项并使其可见（避免拖拽过程中频繁触发导致主线程掉帧卡顿）
    LaunchedEffect(pagerState.settledPage) {
        if (!isUserDraggingList && !pagerState.isScrollInProgress && !listState.isScrollInProgress) {
            val targetScroll = (pagerState.settledPage - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    // 响应外部播放歌曲变更或手动定位按钮触发时，自动驱动 Cover Flow 居中
    LaunchedEffect(currentPlayingSongId, locateTrigger) {
        if (currentPlayingSongId != null) {
            val targetIdx = songs.indexOfFirst { it.id == currentPlayingSongId }
            if (targetIdx >= 0) {
                if (pagerState.currentPage != targetIdx) {
                    pagerState.animateScrollToPage(targetIdx)
                }
                if (locateTrigger > 0L) {
                    val targetScroll = (targetIdx - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(targetScroll)
                }
            }
        }
    }

    val currentSelectedSong = songs.getOrNull(pagerState.currentPage)

    // 用户可自由开关 Cover Flow 下方的联动列表
    var isListVisible by rememberSaveable { mutableStateOf(true) }

    // 监听列表滚动到顶时的过度向下滑动手势以收起列表
    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 当列表处于顶部且继续向下拉动（available.y > 40f）时，收起列表
                if (available.y > 40f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    if (isListVisible) {
                        isListVisible = false
                    }
                }
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.background)
    ) {
        val totalHeight = maxHeight
        val totalWidth = maxWidth
        val baseStageHeight = if (isLandscape) 316.dp else 350.dp
        val listHeight = (totalHeight - baseStageHeight).coerceAtLeast(0.dp)

        val stageHeight: Dp by animateDpAsState(
            targetValue = if (isListVisible) baseStageHeight else totalHeight,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "StageHeight"
        )

        // ==================== 上半部：3D Cover Flow 舞台 ====================
        val cardSize: Dp by animateDpAsState(
            targetValue = when {
                !isListVisible -> if (isLandscape) 235.dp else 210.dp
                isLandscape -> 170.dp
                else -> 155.dp
            },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "CardSize"
        )
        // 倒影高度严格设置为封面的 2/3
        val reflectionHeight = cardSize * (2f / 3f)

        val stageTopPadding: Dp by animateDpAsState(
            targetValue = if (isListVisible) {
                if (isLandscape) 16.dp else 24.dp
            } else {
                if (isLandscape) 36.dp else 28.dp
            },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "StageTopPadding"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.45f), // 顶部聚光暗角
                            0.35f to Color.Transparent,               // 封面主体通透亮区
                            0.50f to Color.Black.copy(alpha = 0.40f), // 倒影接触线下方迅速暗化
                            0.75f to Color.Black.copy(alpha = 0.75f), // 倒影下半部分（红框区）深度暗化
                            1.00f to Color.Black.copy(alpha = 0.92f)  // 舞台底部深邃黑色基底
                        )
                    )
                )
                .pointerInput(Unit) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onVerticalDrag = { _, dragAmount ->
                            totalDragY += dragAmount
                            // 手势向上滑动（dragAmount < 0）超过阈值展开列表
                            if (totalDragY < -35f && !isListVisible) {
                                isListVisible = true
                                totalDragY = 0f
                            } else if (totalDragY > 35f && isListVisible) {
                                // 手势向下滑动（dragAmount > 0）超过阈值收起列表
                                isListVisible = false
                                totalDragY = 0f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            val containerWidth = totalWidth
            val density = LocalDensity.current
            // 核心关键：必须使用与 Pager 内部 PageSize.Fixed 绝对一致的 roundToPx() 整数像素，杜绝亚像素舍入累乘导致的远端卡片剧烈抖动
            val cardWidthPx = with(density) { cardSize.roundToPx().toFloat() }
            val containerWidthPx = with(density) { containerWidth.roundToPx().toFloat() }

            // 核心突破：将 Pager 测量视口向两侧大幅扩容各 1400dp（总宽扩展 2800dp），
            // 使得两侧各 8~10 张封面均 100% 处于 Pager 原生 Viewport（活跃视口）范围内，
            // 彻底为两侧密集多封面展示提供强力底层渲染保障，绝不触发跳帧优化
            val extraViewportWidth = 2800.dp
            val extraViewportWidthPx = with(density) { extraViewportWidth.roundToPx().toFloat() }
            val expandedWidthPx = containerWidthPx + extraViewportWidthPx
            val horizontalContentPadding = with(density) {
                (((expandedWidthPx - cardWidthPx) / 2f).toInt()).toDp().coerceAtLeast(0.dp)
            }


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = stageTopPadding,
                        bottom = if (!isListVisible) bottomPadding.coerceAtMost(60.dp) else 0.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (!isListVisible) Arrangement.Center else Arrangement.Top
            ) {
                    // 3D 封面水平滚动栏（限制触摸与绘制于可用内容宽度内，杜绝向外溢出阻挡侧边栏）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardSize + reflectionHeight + 6.dp)
                            .clipToBounds()
                    ) {
                        // 相机距离（透视强度）：与原先 graphicsLayer 内部取值保持一致
                        val cameraDistancePx = 20f * density.density

                        HorizontalPager(
                            state = pagerState,
                            pageSize = PageSize.Fixed(cardSize), // 严格锁定槽位尺寸，杜绝跨分辨率浮点舍入导致的远端位移抖动
                            contentPadding = PaddingValues(horizontal = horizontalContentPadding),
                            pageSpacing = 0.dp,
                            beyondBoundsPageCount = 9, // 预加载两侧至 9 张卡片，完美覆盖 8.2 张可视消隐边界，大幅减轻高速惯性滑行负担
                            flingBehavior = flingBehavior,
                            modifier = Modifier
                                .fillMaxHeight()
                                .layout { measurable, constraints ->
                                    val looseConstraints = constraints.copy(
                                        minWidth = expandedWidthPx.toInt(),
                                        maxWidth = expandedWidthPx.toInt()
                                    )
                                    val placeable = measurable.measure(looseConstraints)
                                    layout(constraints.maxWidth, placeable.height) {
                                        val offsetX = -((expandedWidthPx - constraints.maxWidth) / 2f).toInt()
                                        placeable.place(offsetX, 0)
                                    }
                                }
                        ) { page ->
                            val song = songs[page]
                            val isCurrentPage = pagerState.currentPage == page
                            val isPlayingThis = currentPlayingSongId == song.id && isCurrentPage

                            // 基于相对当前页偏移的绝对正数层叠景深：越靠近中心越高，左右对称递减
                            val baseZIndex = when {
                                page < pagerState.currentPage -> 500f + (page - pagerState.currentPage)
                                page > pagerState.currentPage -> 500f - (page - pagerState.currentPage)
                                else -> 1000f
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = cardSize, height = cardSize + reflectionHeight)
                                    .zIndex(baseZIndex)
                                    .coverFlowPageTransform(
                                        page = page,
                                        pagerState = pagerState,
                                        cardWidthPx = cardWidthPx,
                                        isLandscape = isLandscape,
                                        cameraDistancePx = cameraDistancePx
                                    )
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            if (pagerState.currentPage == page) {
                                                onSongClick(song, page)
                                            } else {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        page = page,
                                                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                                    )
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (pagerState.currentPage == page) {
                                                showCoverOptions = true
                                            } else {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        page = page,
                                                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                                    )
                                                }
                                            }
                                        }
                                    )
                            ) {
                                // 居中封面挂载长按选项菜单
                                if (isCurrentPage) {
                                    DropdownMenu(
                                        expanded = showCoverOptions,
                                        onDismissRequest = { showCoverOptions = false },
                                        modifier = Modifier
                                            .background(OrbitTheme.colors.surfaceCard)
                                            .border(0.5.dp, OrbitTheme.colors.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    ) {
                                        // 选项菜单标题
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.cover_flow_options_title),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrbitTheme.colors.primary
                                                )
                                            },
                                            onClick = {},
                                            enabled = false
                                        )

                                        // 1. 封面滑动惯性选项
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.cover_flow_inertia_title),
                                                            fontSize = 13.sp,
                                                            color = OrbitTheme.colors.textPrimary
                                                        )
                                                        if (effectiveInertia) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = OrbitTheme.colors.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = if (effectiveInertia) "已开启 (多页惯性滑动)" else "已关闭 (单页精确吸附)",
                                                        fontSize = 10.sp,
                                                        color = OrbitTheme.colors.textSecondary
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Speed,
                                                    contentDescription = null,
                                                    tint = if (effectiveInertia) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                toggleInertia(!effectiveInertia)
                                                showCoverOptions = false
                                            }
                                        )

                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.8f)
                                        )

                                        // 2. 播放/暂停
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (isPlayingThis && isPlaying) "暂停播放" else "开始播放",
                                                    fontSize = 13.sp,
                                                    color = OrbitTheme.colors.textPrimary
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (isPlayingThis && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = OrbitTheme.colors.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                onSongClick(song, page)
                                                showCoverOptions = false
                                            }
                                        )

                                        // 3. 收藏/取消收藏
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (song.isFavorite) "取消收藏" else "收藏歌曲",
                                                    fontSize = 13.sp,
                                                    color = OrbitTheme.colors.textPrimary
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (song.isFavorite) Color(0xFFFF3366) else OrbitTheme.colors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                onFavoriteClick(song)
                                                showCoverOptions = false
                                            }
                                        )

                                        // 4. 更多歌曲选项 (调用原本传入的 onLongClick)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "更多操作与详情...",
                                                    fontSize = 13.sp,
                                                    color = OrbitTheme.colors.textSecondary
                                                )
                                            },
                                            onClick = {
                                                showCoverOptions = false
                                                onLongClick(song)
                                            }
                                        )
                                    }
                                }
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // 1. 主体封面 (移除底部阴影，保证底边缘与倒影顶边缘零距离贴合)
                                    val artUri = song.albumArtUri ?: AudioCoverProvider.buildSongCoverUri(song.id, song.path, song.album)
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 1.dp, bottomEnd = 1.dp),
                                        shadowElevation = 0.dp,
                                        border = BorderStroke(
                                            width = if (isPlayingThis) 1.5.dp else 0.5.dp,
                                            color = if (isPlayingThis) OrbitTheme.colors.primary else Color.White.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.size(cardSize)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(OrbitTheme.colors.surfaceCard),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            SubcomposeAsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(artUri)
                                                    .memoryCacheKey("${artUri}_$coverVersion")
                                                    .diskCacheKey("${artUri}_$coverVersion")
                                                    .size(Size(360, 360))
                                                    .allowHardware(true)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = song.title,
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = OrbitTheme.colors.primary.copy(alpha = 0.3f),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            if (isPlayingThis) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(6.dp)
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(OrbitTheme.colors.primary.copy(alpha = 0.88f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 2. 真实拟物镜面倒影 (Reflection) - 直接反转封面，高度占封面的 2/3，从上往下平滑减弱消隐
                                    Box(
                                        modifier = Modifier
                                            .size(width = cardSize, height = reflectionHeight)
                                            .clipToBounds()
                                            .graphicsLayer {
                                                compositingStrategy = CompositingStrategy.Offscreen
                                            }
                                            .drawWithContent {
                                                drawContent()
                                                // 倒影透明度：从顶部85%不透明平滑过渡到底部100%完全透明消隐
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.White.copy(alpha = 0.85f), // 顶部接触线：85% 不透明
                                                            Color.White.copy(alpha = 0.35f), // 中部平滑衰减
                                                            Color.Transparent                // 底部：100% 完全透明消隐
                                                        )
                                                    ),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    this.scaleY = -1f // 直接垂直反转封面，原封面底边像素精准对齐倒影顶部接缝
                                                }
                                                .background(OrbitTheme.colors.surfaceCard)
                                        ) {
                                            SubcomposeAsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(artUri)
                                                    .memoryCacheKey("${artUri}_$coverVersion")
                                                    .diskCacheKey("${artUri}_$coverVersion")
                                                    .size(Size(360, 360))
                                                    .allowHardware(true)
                                                    .build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                alignment = Alignment.BottomCenter,
                                                loading = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = OrbitTheme.colors.primary.copy(alpha = 0.3f),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = OrbitTheme.colors.textSecondary.copy(alpha = 0.35f),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 居中展示当前选中项的标题与副标题信息
                    if (currentSelectedSong != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = currentSelectedSong.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrbitTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${currentSelectedSong.artist} • ${currentSelectedSong.album}",
                                fontSize = 11.sp,
                                color = OrbitTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

        // 下半部：协同联动歌曲列表（支持用户手势向上滑入展开与向下滑出收起）
        AnimatedVisibility(
            visible = isListVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 260)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(listHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OrbitTheme.colors.background)
            ) {
                // 顶部收起拖拽条指示器（支持点击或向下滑动收起列表）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                    if (totalDrag > 30f) {
                                        isListVisible = false
                                        totalDrag = 0f
                                    }
                                }
                            )
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            isListVisible = false
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OrbitTheme.colors.textSecondary.copy(alpha = 0.35f))
                    )
                }

                // 分割细线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(OrbitTheme.colors.surfaceCard.copy(alpha = 0.8f))
                )

                // ==================== 协同联动歌曲列表 ====================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 6.dp,
                            bottom = bottomPadding + 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                            .pointerInput(Unit) {
                                // 监听用户在列表上的触碰拖动，以防覆盖 Cover Flow
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        isUserDraggingList = event.changes.any { it.pressed }
                                    }
                                }
                            }
                    ) {
                        itemsIndexed(
                            items = songs,
                            key = { index, song -> "${song.id}_$index" }
                        ) { index, song ->
                            val isSelectedInCoverFlow = pagerState.currentPage == index
                            val isSongPlaying = currentPlayingSongId == song.id

                            val rowBgColor by animateColorAsState(
                                targetValue = when {
                                    isSelectedInCoverFlow -> OrbitTheme.colors.primary.copy(alpha = 0.18f)
                                    isSongPlaying -> OrbitTheme.colors.primary.copy(alpha = 0.08f)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(180),
                                label = "RowBg"
                            )

                            val rowBorderColor = if (isSelectedInCoverFlow) {
                                OrbitTheme.colors.primary.copy(alpha = 0.45f)
                            } else {
                                Color.Transparent
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(rowBgColor)
                                    .border(BorderStroke(0.8.dp, rowBorderColor), RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (pagerState.currentPage == index) {
                                                // 已经在 Cover Flow 居中：再次点击触发播放
                                                onSongClick(song, index)
                                            } else {
                                                // 点击其他行：平滑将 Cover Flow 滑动到该歌曲
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            }
                                        },
                                        onLongClick = { onLongClick(song) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                // 序号 / 当前播放指示图标
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.width(30.dp)
                                ) {
                                    if (isSongPlaying) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = null,
                                            tint = OrbitTheme.colors.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            color = if (isSelectedInCoverFlow) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                            fontWeight = if (isSelectedInCoverFlow) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // 歌曲名与艺术家信息
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelectedInCoverFlow || isSongPlaying) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelectedInCoverFlow || isSongPlaying) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${song.artist} • ${song.album}",
                                        fontSize = 11.sp,
                                        color = OrbitTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 时长展示
                                Text(
                                    text = song.formattedDuration,
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                // 收藏按钮
                                IconButton(
                                    onClick = { onFavoriteClick(song) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    val (favIcon, favColor) = when {
                                        song.isFavorite -> Icons.Default.Favorite to Color(0xFFFF3366)
                                        song.isDisliked -> Icons.Default.ThumbDown to Color(0xFFE57373)
                                        else -> Icons.Default.FavoriteBorder to OrbitTheme.colors.textSecondary.copy(alpha = 0.6f)
                                    }
                                    Icon(
                                        imageVector = favIcon,
                                        contentDescription = "Favorite",
                                        tint = favColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cover Flow 单页变换（水平位移 / Y 轴旋转 / 缩放）
 *
 * 核心原理：所有视觉变换（位移 / 旋转 / 缩放）统一在 graphicsLayer（GPU 渲染图层）的 Draw 阶段执行。
 * 1. 杜绝在 Modifier.layout placement 阶段修改物理摆放坐标（placeWithLayer(intTranslationX, 0)），
 *    因为 Compose 的 LazyLayout 对视口外（BeyondBounds）边缘卡片的 placement 存在延迟/跳过优化，
 *    会导致两边第三张封面之外的卡片在 placement 阶段出现一帧更新、一帧回退到原槽位，从而产生新旧位置交替的弹簧抖动。
 * 2. 纯 graphicsLayer 变换不改变布局树坐标，状态读取直接驱动 GPU 图层矩阵，
 *    无论距离中心多远，每一帧渲染均与当前滑动偏移严格同步，彻底根除远端封面弹簧来回抖动问题。
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.coverFlowPageTransform(
    page: Int,
    pagerState: PagerState,
    cardWidthPx: Float,
    isLandscape: Boolean,
    cameraDistancePx: Float
): Modifier = this.graphicsLayer {
    // 采用官方标准 API 精准获取当前页面的连续滑动偏移（右侧为正，左侧为负），避免单独读取两个状态引发的时间差
    val offset = -pagerState.getOffsetFractionForPage(page)
    val absOffset = offset.absoluteValue
    val sign = if (offset >= 0f) 1f else -1f

    // 与 Pager 槽位严格一致的固定像素宽度，杜绝浮点误差累加
    val centerSpread = cardWidthPx * (if (isLandscape) 0.60f else 0.56f)
    val wingOverlapStep = cardWidthPx * (if (isLandscape) 0.24f else 0.20f)

    // 严谨平滑过渡函数
    val targetX = if (absOffset <= 1f) {
        offset * centerSpread
    } else {
        sign * (centerSpread + (absOffset - 1f) * wingOverlapStep)
    }
    val rawX = offset * cardWidthPx
    val translationPx = targetX - rawX

    val maxRotationAngle = 48f
    val rotation = if (absOffset <= 1f) {
        -offset * maxRotationAngle
    } else {
        -sign * maxRotationAngle
    }
    val scale = when {
        absOffset <= 1f -> 1f - absOffset * 0.10f
        else -> (0.90f - (absOffset - 1f) * 0.015f).coerceAtLeast(0.76f)
    }

    // 经典 Cover Flow 豪华立体多封面展示与边缘平滑消隐：
    // 核心可视区（当前及两侧前 6 张封面，共 13 张大容量展现）保持 100% 全清通透；
    // 远端边缘（第 6.2 张到第 8.2 张）自然平滑渐隐融入深黑舞台背景；
    // 超过第 8.2 张之外完全透明隐形（alpha = 0f），进出边缘均在透明状态下无感过渡
    val edgeAlpha = when {
        absOffset <= 6.2f -> 1f
        absOffset >= 8.2f -> 0f
        else -> (1f - (absOffset - 6.2f) / 2.0f).coerceIn(0f, 1f)
    }

    translationX = translationPx
    rotationY = rotation
    scaleX = scale
    scaleY = scale
    cameraDistance = cameraDistancePx
    alpha = edgeAlpha
}
