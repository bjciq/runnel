package io.github.xccxcxxc.runnel.data

import androidx.compose.runtime.mutableStateMapOf

/**
 * 本次运行期间已读/星标的最新状态（覆盖列表快照里的旧值）。
 *
 * 文章列表打开时取一次快照，之后不重新查询（标为已读的文章不会从“未读”列表里突然消失），行上显示的状态以这里为准。只在主线程写。
 */
class FlagStore {
    val read = mutableStateMapOf<Long, Boolean>()
    val starred = mutableStateMapOf<Long, Boolean>()

    fun setRead(ids: Collection<Long>, value: Boolean) = ids.forEach { read[it] = value }
    fun setStarred(ids: Collection<Long>, value: Boolean) = ids.forEach { starred[it] = value }

    fun clear() {
        read.clear()
        starred.clear()
    }
}
