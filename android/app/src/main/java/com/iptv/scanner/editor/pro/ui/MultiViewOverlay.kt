package com.iptv.scanner.editor.pro.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.iptv.scanner.editor.pro.player.ExoPlayerController
import com.iptv.scanner.editor.pro.player.ExoPlayerView
import com.iptv.scanner.editor.pro.ui.theme.tvFocusBorder

/**
 * 多画面网格容器。
 *
 * 根据 [MultiViewState.layout] 渲染网格：
 * - DUAL：左右分屏（主画面左，副画面右）
 * - QUAD：2x2 网格（主画面左上，副画面右上/左下/右下）
 *
 * 主画面（index=0）由 [primaryContent] Composable 渲染（复用 MainPlayerScreen 现有播放器 View 逻辑）。
 * 副画面（index=1+）由本组件内部创建 [ExoPlayerView] 渲染。
 *
 * @param state 多画面状态
 * @param primaryContent 主画面内容 Composable
 * @param getSubPlayer 获取副画面 Player 实例（按 index）
 * @param onViewportClick 视口点击回调（TV 端焦点切换 / 手机端点击添加频道）
 * @param onViewportClose 视口关闭回调（副画面关闭按钮）
 * @param modifier Modifier
 */
@Composable
fun MultiViewOverlay(
    state: MultiViewState,
    primaryContent: @Composable BoxScope.() -> Unit,
    getSubPlayer: (Int) -> com.iptv.scanner.editor.pro.player.Player?,
    onViewportClick: (Int) -> Unit,
    onViewportClose: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.active) return

    val focusedColor = MaterialTheme.colorScheme.primary
    val unfocusedColor = Color.White.copy(alpha = 0.2f)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (state.layout) {
            MultiViewLayout.DUAL -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 主画面（左）
                ViewportCell(
                    viewport = state.viewports.getOrNull(0),
                    isFocused = state.focusedIndex == 0,
                    focusedColor = focusedColor,
                    unfocusedColor = unfocusedColor,
                    onClick = { onViewportClick(0) },
                    onClose = null,  // 主画面不能关闭
                    modifier = Modifier.weight(1f)
                ) {
                    primaryContent()
                }
                // 副画面（右）
                ViewportCell(
                    viewport = state.viewports.getOrNull(1),
                    isFocused = state.focusedIndex == 1,
                    focusedColor = focusedColor,
                    unfocusedColor = unfocusedColor,
                    onClick = { onViewportClick(1) },
                    onClose = { onViewportClose(1) },
                    modifier = Modifier.weight(1f)
                ) {
                    SubViewportContent(
                        viewportIndex = 1,
                        viewport = state.viewports.getOrNull(1),
                        getSubPlayer = getSubPlayer
                    )
                }
            }
            MultiViewLayout.QUAD -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 上排
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 主画面（左上）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(0),
                        isFocused = state.focusedIndex == 0,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(0) },
                        onClose = null,
                        modifier = Modifier.weight(1f)
                    ) {
                        primaryContent()
                    }
                    // 副画面（右上）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(1),
                        isFocused = state.focusedIndex == 1,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(1) },
                        onClose = { onViewportClose(1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        SubViewportContent(
                            viewportIndex = 1,
                            viewport = state.viewports.getOrNull(1),
                            getSubPlayer = getSubPlayer
                        )
                    }
                }
                // 下排
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 副画面（左下）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(2),
                        isFocused = state.focusedIndex == 2,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(2) },
                        onClose = { onViewportClose(2) },
                        modifier = Modifier.weight(1f)
                    ) {
                        SubViewportContent(
                            viewportIndex = 2,
                            viewport = state.viewports.getOrNull(2),
                            getSubPlayer = getSubPlayer
                        )
                    }
                    // 副画面（右下）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(3),
                        isFocused = state.focusedIndex == 3,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(3) },
                        onClose = { onViewportClose(3) },
                        modifier = Modifier.weight(1f)
                    ) {
                        SubViewportContent(
                            viewportIndex = 3,
                            viewport = state.viewports.getOrNull(3),
                            getSubPlayer = getSubPlayer
                        )
                    }
                }
            }
            MultiViewLayout.SINGLE -> {
                // SINGLE 模式不应该进入多画面，这里兜底
                primaryContent()
            }
        }
    }
}

/**
 * 单个视口 Cell（包含边框、关闭按钮、内容）。
 */
@Composable
private fun BoxScope.ViewportCell(
    viewport: MultiViewport?,
    isFocused: Boolean,
    focusedColor: Color,
    unfocusedColor: Color,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor = if (isFocused) focusedColor else unfocusedColor
    val borderWidth = if (isFocused) 3.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .background(Color.Black)
            .clickable { onClick() }
    ) {
        // 内容
        content()

        // 空画面提示
        if (viewport != null && viewport.isEmpty) {
            EmptyViewportHint(
                text = "点击添加频道",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 错误画面提示
        if (viewport != null && viewport.isError) {
            ErrorViewportHint(
                channelName = viewport.channelName,
                errorMessage = viewport.errorMessage,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 频道名标签（左上角）
        if (viewport != null && !viewport.isEmpty) {
            ChannelLabel(
                text = viewport.channelName,
                isPrimary = viewport.isPrimary,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        // 关闭按钮（右上角，仅副画面）
        if (onClose != null && viewport != null && !viewport.isEmpty) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 副画面内容（ExoPlayerView）。
 *
 * player 用 remember(viewportIndex, viewport.channelIdx) 确保频道变化时重新获取
 * （添加频道时 player 从 null 变为非 null）。
 * AndroidView 不用 key 包裹 channelIdx，避免切换频道时重建 View（ExoPlayer 实例
 * 不变，player.playFile 切换 URL 即可，View 重建会导致短暂黑屏）。
 */
@Composable
private fun SubViewportContent(
    viewportIndex: Int,
    viewport: MultiViewport?,
    getSubPlayer: (Int) -> com.iptv.scanner.editor.pro.player.Player?
) {
    if (viewport == null || viewport.isEmpty) {
        // 空画面不创建 View
        return
    }

    // channelIdx 作为 key：添加频道时（-1 → 实际值）重新获取 player
    val player = remember(viewportIndex, viewport.channelIdx) {
        getSubPlayer(viewportIndex) as? ExoPlayerController
    }

    if (player != null) {
        AndroidView(
            factory = { ctx ->
                ExoPlayerView(ctx).also { view ->
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player.attachView(view)
                }
            },
            update = { /* ExoPlayerView surface 回调内部处理 */ },
            onRelease = { view ->
                // 不在这里 detach（detach 由 ViewModel.exitMultiView 统一管理）
                // 只清除 View 内部引用
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun EmptyViewportHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ErrorViewportHint(channelName: String, errorMessage: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = Color.Red.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = channelName,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = errorMessage,
            color = Color.Red.copy(alpha = 0.8f),
            fontSize = 10.sp,
            maxLines = 2
        )
    }
}

@Composable
private fun ChannelLabel(text: String, isPrimary: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isPrimary) Color(0xFF4A9EFF) else Color.Black.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isPrimary) "主: $text" else text,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
