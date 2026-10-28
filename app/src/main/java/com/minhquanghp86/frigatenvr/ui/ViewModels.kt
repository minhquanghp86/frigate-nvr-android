package com.minhquanghp86.frigatenvr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minhquanghp86.frigatenvr.FrigateApp
import com.minhquanghp86.frigatenvr.data.DayRecordings
import com.minhquanghp86.frigatenvr.data.FrigateApiException
import com.minhquanghp86.frigatenvr.data.FrigateConfig
import com.minhquanghp86.frigatenvr.data.FrigateEvent
import com.minhquanghp86.frigatenvr.data.FrigateNet
import com.minhquanghp86.frigatenvr.data.FrigateRepository
import com.minhquanghp86.frigatenvr.data.IdsBody
import com.minhquanghp86.frigatenvr.data.ReviewItem
import com.minhquanghp86.frigatenvr.data.VersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject

private fun repoOf(app: Application): FrigateRepository =
    (app as FrigateApp).container.repository

/** Cấu hình Frigate (danh sách camera) cho màn Camera. */
class CamerasViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = repoOf(app)

    private val _state = MutableStateFlow<UiState<FrigateConfig>>(UiState.Loading)
    val state: StateFlow<UiState<FrigateConfig>> = _state.asStateFlow()

    private val _latest = MutableStateFlow<Map<String, FrigateEvent>>(emptyMap())
    val latest: StateFlow<Map<String, FrigateEvent>> = _latest.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val cfg = try {
                repo.configCached()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Không tải được cấu hình Frigate.")
                return@launch
            }
            _state.value = UiState.Ok(cfg)
            _latest.value = cfg.enabledCameras.mapNotNull { cam ->
                val ev = runCatching {
                    repo.call {
                        events(
                            mapOf(
                                "cameras" to cam,
                                "limit" to "1",
                                "include_thumbnails" to "0",
                            ),
                        )
                    }.firstOrNull()
                }.getOrNull()
                ev?.let { cam to it }
            }.toMap()
        }
    }

    fun repo(): FrigateRepository = repo
}

/** Danh sách sự kiện + bộ lọc + phân trang. */
class EventsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = repoOf(app)

    private val _items = MutableStateFlow<List<FrigateEvent>>(emptyList())
    val items: StateFlow<List<FrigateEvent>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _camera = MutableStateFlow<String?>(null)
    val camera: StateFlow<String?> = _camera.asStateFlow()

    private val _label = MutableStateFlow<String?>(null)
    val label: StateFlow<String?> = _label.asStateFlow()

    private val _onlyWithClip = MutableStateFlow(false)
    val onlyWithClip: StateFlow<Boolean> = _onlyWithClip.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value.trim()
        refresh()
    }

    private val _cameras = MutableStateFlow<List<String>>(emptyList())
    val cameras: StateFlow<List<String>> = _cameras.asStateFlow()

    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            var first = true
            repo.dataVersion.collect {
                if (first) {
                    first = false
                    return@collect
                }
                refresh()
            }
        }
        viewModelScope.launch {
            _cameras.value = try {
                repo.configCached().enabledCameras
            } catch (_: Exception) {
                emptyList()
            }
            _labels.value = try {
                repo.labels()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun setCamera(value: String?) {
        _camera.value = value
        refresh()
    }

    fun setLabel(value: String?) {
        _label.value = value
        refresh()
    }

    fun setOnlyWithClip(value: Boolean) {
        _onlyWithClip.value = value
        refresh()
    }

    fun refresh() {
        _items.value = emptyList()
        _hasMore.value = true
        loadMore()
    }

    fun loadMore() {
        if (_loading.value || !_hasMore.value) return
        val before = _items.value.minOfOrNull { it.startTime }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val params = buildMap {
                    put("limit", "50")
                    put("include_thumbnails", "0")
                    _camera.value?.takeIf { it.isNotBlank() }?.let { put("cameras", it) }
                    _label.value?.takeIf { it.isNotBlank() }?.let { put("labels", it) }
                    if (_onlyWithClip.value) put("has_clip", "1")
                    if (before != null) put("before", before.toString())
                }
                val q = _query.value
                val list = if (q.isNotBlank()) {
                    repo.call { eventsSearch(mapOf("query" to q, "limit" to "50")) }
                } else {
                    repo.call { events(params) }
                }
                val known = _items.value.map { it.id }.toSet()
                val merged = _items.value + list.filterNot { it.id in known }
                _items.value = merged
                _hasMore.value = list.isNotEmpty()
            } catch (e: Exception) {
                _error.value = e.message ?: "Không tải được sự kiện."
            } finally {
                _loading.value = false
            }
        }
    }
}

/** Màn "Cần xem" (Review items của Frigate). */
class ReviewViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = repoOf(app)

    private val _items = MutableStateFlow<List<ReviewItem>>(emptyList())
    val items: StateFlow<List<ReviewItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unreadOnly = MutableStateFlow(true)
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    init { refresh() }

    fun setUnreadOnly(value: Boolean) {
        _unreadOnly.value = value
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val params = buildMap {
                    put("limit", "50")
                    // Frigate 0.18: reviewed là số (0 = chưa xem, 1 = đã xem), không phải boolean.
                    put("reviewed", if (_unreadOnly.value) "0" else "1")
                }
                _items.value = repo.call { reviews(params) }
            } catch (e: Exception) {
                _error.value = e.message ?: "Không tải được danh sách cần xem."
            } finally {
                _loading.value = false
            }
        }
    }

    fun markViewed(item: ReviewItem) {
        viewModelScope.launch {
            try {
                repo.call { markViewed(IdsBody(listOf(item.id))) }
                _items.value = _items.value.filterNot { it.id == item.id }
            } catch (e: Exception) {
                _error.value = e.message ?: "Không đánh dấu được."
            }
        }
    }
}

/** Màn "Xem lại": ngày có recording -> khung giờ -> phát HLS. */
class TimelineViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = repoOf(app)

    private val _cameras = MutableStateFlow<List<String>>(emptyList())
    val cameras: StateFlow<List<String>> = _cameras.asStateFlow()

    private val _labels = MutableStateFlow<List<String>>(emptyList())
    val labels: StateFlow<List<String>> = _labels.asStateFlow()

    private val _camera = MutableStateFlow<String?>(null)
    val camera: StateFlow<String?> = _camera.asStateFlow()

    private val _days = MutableStateFlow<List<String>>(emptyList())
    val days: StateFlow<List<String>> = _days.asStateFlow()

    private val _hours = MutableStateFlow<List<DayRecordings>>(emptyList())
    val hours: StateFlow<List<DayRecordings>> = _hours.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private val timezone: String = java.util.TimeZone.getDefault().id

    init { refresh() }

    fun refresh() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val config = repo.call { config() }
                _cameras.value = config.enabledCameras
                if (_camera.value == null) _camera.value = _cameras.value.firstOrNull()
                val days = repo.call { recordingsSummary(timezone) }
                _days.value = days.filterValues { it }.keys.sortedDescending()
                loadHours()
            } catch (e: Exception) {
                _error.value = e.message ?: "Không tải được dữ liệu ghi hình."
            } finally {
                _loading.value = false
            }
        }
    }

    fun setCamera(value: String) {
        _camera.value = value
        _playing.value = null
        loadHours()
    }

    private fun loadHours() {
        val camera = _camera.value ?: return
        viewModelScope.launch {
            try {
                _hours.value = repo.call { cameraRecordingsSummary(camera, timezone) }
            } catch (e: Exception) {
                _error.value = e.message ?: "Không tải được khung giờ ghi hình."
            }
        }
    }

    fun play(day: String, hour: String) {
        val camera = _camera.value ?: return
        _playing.value = repo.vodHour(day, hour, camera)
    }

    fun stop() {
        _playing.value = null
    }

    fun repo(): FrigateRepository = repo
}

/** Màn Cài đặt: phiên, phiên bản Frigate, chỉ số sức khỏe. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = repoOf(app)

    private val _version = MutableStateFlow<UiState<VersionInfo>?>(null)
    val version: StateFlow<UiState<VersionInfo>?> = _version.asStateFlow()

    private val _stats = MutableStateFlow<JsonObject?>(null)
    val stats: StateFlow<JsonObject?> = _stats.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun repo(): FrigateRepository = repo

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _version.value = try {
                val text = repo.call { version() }.string().trim()
                UiState.Ok(parseVersion(text))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Không đọc được phiên bản Frigate.")
            }
            _stats.value = try {
                val body = repo.call { stats() }
                kotlinx.serialization.json.Json.parseToJsonElement(body.string())
                    as? JsonObject
            } catch (_: Exception) {
                null
            }
        }
    }

    fun save(
        baseUrl: String,
        username: String,
        password: String,
        remember: Boolean,
        trust: Boolean,
        go2rtcUrl: String? = null,
    ) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = repo.updateSettings(
                baseUrlRaw = baseUrl,
                username = username,
                password = password.ifBlank { null },
                remember = remember,
                trustSelfSigned = trust,
                go2rtcUrl = go2rtcUrl,
            ) ?: "Đã lưu cài đặt."
            _busy.value = false
            refresh()
        }
    }

    fun logout() {
        viewModelScope.launch { repo.signOut() }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** Dùng chung khi cần gọi API lấy cấu hình ở màn hình con (vd: chi tiết sự kiện). */
suspend fun loadCameras(repo: FrigateRepository): List<String> =
    try {
        repo.call { config() }.enabledCameras
    } catch (e: FrigateApiException) {
        emptyList()
    } catch (_: Exception) {
        emptyList()
    }

/** /api/version: Frigate 0.18 trả chuỗi thuần, bản cũ hơn trả JSON object. */
private fun parseVersion(text: String): VersionInfo {
    if (text.startsWith("{")) {
        runCatching { FrigateNet.json.decodeFromString<VersionInfo>(text) }
            .getOrNull()
            ?.let { return it }
    }
    return VersionInfo(version = text.ifBlank { null })
}
