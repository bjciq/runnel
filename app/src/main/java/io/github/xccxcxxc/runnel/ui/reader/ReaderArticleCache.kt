package io.github.xccxcxxc.runnel.ui.reader

import io.github.xccxcxxc.runnel.data.db.ReaderArticle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** 阅读页各入口共用一次正文查询；由页面 scope 持有，单个消费者退出不会取消其他人的加载。仅在主线程调用。 */
internal class ReaderArticleCache(
    private val scope: CoroutineScope,
    private val capacity: Int = 24,
    private val read: suspend (Long) -> ReaderArticle?,
) {
    private val cached = LinkedHashMap<Long, ReaderArticle>(16, 0.75f, true)
    private val pending = HashMap<Long, Deferred<ReaderArticle?>>()

    init { require(capacity > 0) }

    suspend fun load(id: Long): ReaderArticle? {
        cached[id]?.let { return it }
        val request = pending.getOrPut(id) {
            // 先登记再开始：缓存命中或数据库极快返回时，也不能留下已完成的 pending 项。
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    read(id)?.also { article ->
                        cached[id] = article
                        if (cached.size > capacity) cached.remove(cached.keys.first())
                    }
                } finally {
                    pending.remove(id)
                }
            }
        }
        return request.await()
    }
}
