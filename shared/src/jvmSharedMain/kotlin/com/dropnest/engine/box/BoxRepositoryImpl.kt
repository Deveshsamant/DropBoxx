package com.dropnest.engine.box

import co.touchlab.kermit.Logger
import com.dropnest.core.nowMillis
import com.dropnest.core.randomId
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.PlatformServices
import com.dropnest.engine.store.JsonFileStore
import com.dropnest.model.BoxItem
import com.dropnest.model.DeviceInfo
import com.dropnest.model.ItemKind
import com.dropnest.model.OutgoingItem
import com.dropnest.model.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class BoxFile(val items: List<BoxItem> = emptyList())

class BoxRepositoryImpl(dataDirectory: String, private val platform: PlatformServices) : BoxRepository {

    private val log = Logger.withTag("Box")
    private val store = JsonFileStore(File(dataDirectory, "box.json"), BoxFile.serializer()) { BoxFile() }
    private val _items = MutableStateFlow(store.load().items)
    override val items: StateFlow<List<BoxItem>> get() = _items

    override suspend fun add(items: List<OutgoingItem>, forPeer: DeviceInfo?): List<String> = withContext(Dispatchers.IO) {
        val skipped = mutableListOf<String>()
        val added = mutableListOf<BoxItem>()
        for (item in items) {
            when (item) {
                is OutgoingItem.Text -> added += BoxItem(randomId(10), ItemKind.TEXT, "message.txt", item.size, "text/plain", nowMillis(), content = item.text)
                is OutgoingItem.Url -> added += BoxItem(randomId(10), ItemKind.URL, item.url, item.size, "text/uri-list", nowMillis(), content = item.url)
                is OutgoingItem.File -> {
                    val file = item.file
                    if (file.size <= 0 || !readable(file)) { skipped += file.name; continue }
                    val id = randomId(10)
                    val locator = runCatching { platform.boxFiles.retain(file, id) }
                        .getOrElse { log.w(it) { "retain failed for ${file.name}" }; skipped += file.name; continue }
                    added += BoxItem(id, ItemKind.FILE, file.name, file.size, file.mimeType, nowMillis(), source = locator)
                }
            }
        }
        val scoped = if (forPeer == null) added else added.map { it.copy(forPeerId = forPeer.id, forPeerAlias = forPeer.alias) }
        if (scoped.isNotEmpty()) commit(_items.value + scoped)
        skipped
    }

    private fun readable(file: PlatformFile): Boolean = runCatching {
        file.open().use { src -> okio.Buffer().let { src.read(it, 1) >= 0 } }
    }.getOrDefault(false)

    @Synchronized
    override fun remove(id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        item.source?.let { runCatching { platform.boxFiles.release(it) } }
        commit(_items.value.filterNot { it.id == id })
    }

    @Synchronized
    override fun clear() {
        _items.value.forEach { it.source?.let { s -> runCatching { platform.boxFiles.release(s) } } }
        commit(emptyList())
    }

    override fun open(id: String): PlatformFile? {
        val item = _items.value.firstOrNull { it.id == id && it.kind == ItemKind.FILE } ?: return null
        val file = item.source?.let { platform.boxFiles.resolve(it) }
        if ((file == null) == item.available) {
            commit(_items.value.map { if (it.id == id) it.copy(available = file != null) else it })
        }
        return file
    }

    override fun toOutgoing(ids: Collection<String>): List<OutgoingItem> = _items.value.filter { it.id in ids }.mapNotNull { item ->
        when (item.kind) {
            ItemKind.TEXT -> OutgoingItem.Text(item.id, item.content.orEmpty())
            ItemKind.URL -> OutgoingItem.Url(item.id, item.content.orEmpty())
            ItemKind.FILE -> open(item.id)?.let { OutgoingItem.File(item.id, it) }
        }
    }

    private fun commit(list: List<BoxItem>) {
        _items.value = list
        store.save(BoxFile(list))
    }
}
