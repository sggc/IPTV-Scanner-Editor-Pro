package com.iptv.scanner.editor.pro.ui

/**
 * 多画面布局模式。
 *
 * - SINGLE：单画面（退出多画面）
 * - DUAL：双画面（左右分屏，主+副）
 * - QUAD：四画面（2x2 网格，主+3副）
 *
 * 主画面始终用当前播放器（通常为 MPV，功能最全），
 * 副画面强制用 ExoPlayer（MPV 在安卓端是单例，不支持多实例）。
 */
enum class MultiViewLayout(val count: Int, val displayName: String) {
    SINGLE(1, "单画面"),
    DUAL(2, "双画面"),
    QUAD(4, "四画面");

    companion object {
        fun fromCount(count: Int): MultiViewLayout =
            when (count) {
                2 -> DUAL
                4 -> QUAD
                else -> SINGLE
            }
    }
}

/**
 * 多画面中的一个视口（viewport）。
 *
 * @param index 视口索引（0=主画面，1-3=副画面）
 * @param channelIdx 频道索引（-1=空画面，未播放）
 * @param isPrimary 是否主画面（主画面有音频，副画面默认静音）
 * @param channelName 频道名（用于 UI 显示，空画面为"未播放"）
 * @param isError 播放错误标志（如协议不支持）
 * @param errorMessage 错误信息
 */
data class MultiViewport(
    val index: Int,
    val channelIdx: Int = -1,
    val isPrimary: Boolean = false,
    val channelName: String = "",
    val isError: Boolean = false,
    val errorMessage: String = ""
) {
    /** 是否为空画面（未播放） */
    val isEmpty: Boolean get() = channelIdx < 0
}

/**
 * 多画面状态快照（UI 观察用）。
 *
 * @param layout 当前布局
 * @param viewports 视口列表（长度 = layout.count）
 * @param focusedIndex 焦点视口索引（TV 端 D-pad 切换焦点）
 * @param active 是否激活多画面模式
 */
data class MultiViewState(
    val active: Boolean = false,
    val layout: MultiViewLayout = MultiViewLayout.DUAL,
    val viewports: List<MultiViewport> = emptyList(),
    val focusedIndex: Int = 0
) {
    /** 主画面视口（index=0） */
    val primaryViewport: MultiViewport? get() = viewports.firstOrNull { it.isPrimary }

    /** 焦点视口 */
    val focusedViewport: MultiViewport? get() = viewports.getOrNull(focusedIndex)

    /** 第一个空闲视口（用于添加新频道） */
    val firstEmptyViewport: MultiViewport?
        get() = viewports.firstOrNull { it.isEmpty }

    companion object {
        /** 创建初始状态（指定布局） */
        fun create(layout: MultiViewLayout, primaryChannelIdx: Int, primaryChannelName: String): MultiViewState {
            val viewports = (0 until layout.count).map { i ->
                if (i == 0) {
                    MultiViewport(
                        index = 0,
                        channelIdx = primaryChannelIdx,
                        isPrimary = true,
                        channelName = primaryChannelName
                    )
                } else {
                    MultiViewport(index = i)
                }
            }
            return MultiViewState(
                active = true,
                layout = layout,
                viewports = viewports,
                focusedIndex = 0
            )
        }
    }
}
