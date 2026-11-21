package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ConnectionLog
import com.example.data.model.LogEventType
import com.example.data.model.SystemSettings
import com.example.data.model.UiState
import com.example.data.model.VpnServer
import com.example.data.repository.VpnRepository
import com.example.util.YamlConfigCodec
import com.example.vpn.NetworkSecurityReport
import com.example.vpn.VpnTunnelManager
import com.example.vpn.VpnTunnelSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(
    private val repository: VpnRepository,
    private val currentUserId: String,
) : ViewModel() {

    val serversState: StateFlow<UiState<List<VpnServer>>> = repository.observeServers(currentUserId)
        .map<List<VpnServer>, UiState<List<VpnServer>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing vpn_servers", rootError)
            emit(UiState.Error(rootError.message ?: "خطا در دریافت لیست سرورهای Firebase"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val settingsState: StateFlow<UiState<SystemSettings?>> = repository.observeSystemSettings(currentUserId)
        .map<SystemSettings?, UiState<SystemSettings?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing system_settings", rootError)
            emit(UiState.Error(rootError.message ?: "خطا در دریافت تنظیمات سیستم"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val logsState: StateFlow<UiState<List<ConnectionLog>>> = repository.observeConnectionLogs(currentUserId)
        .map<List<ConnectionLog>, UiState<List<ConnectionLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing connection_logs", rootError)
            emit(UiState.Error(rootError.message ?: "خطا در دریافت گزارش‌ها"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val tunnelSnapshot: StateFlow<VpnTunnelSnapshot> = VpnTunnelManager.snapshot

    private val _isBootstrapping = MutableStateFlow(false)
    val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

    private val _isPingingAll = MutableStateFlow(false)
    val isPingingAll: StateFlow<Boolean> = _isPingingAll.asStateFlow()

    private val _pingingServerIds = MutableStateFlow<Set<String>>(emptySet())
    val pingingServerIds: StateFlow<Set<String>> = _pingingServerIds.asStateFlow()

    private val _isCheckingSecurity = MutableStateFlow(false)
    val isCheckingSecurity: StateFlow<Boolean> = _isCheckingSecurity.asStateFlow()

    private val _securityReport = MutableStateFlow<NetworkSecurityReport?>(null)
    val securityReport: StateFlow<NetworkSecurityReport?> = _securityReport.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        bootstrapDefaults()
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun showFeedback(message: String) {
        _feedbackMessage.value = message
    }

    fun bootstrapDefaults() {
        viewModelScope.launch {
            _isBootstrapping.value = true
            val result = repository.bootstrapOwnerDefaultsIfEmpty(currentUserId)
            result.onSuccess { seeded ->
                if (seeded) {
                    _feedbackMessage.value = "۷ سرور استاندارد جهانی در Firebase ثبت و سامانه فعال شد."
                }
            }.onFailure { error ->
                _feedbackMessage.value = error.localizedMessage ?: "خطا در راه‌اندازی اولیه Firebase"
            }
            _isBootstrapping.value = false
        }
    }

    fun runSecurityCheck() {
        if (_isCheckingSecurity.value) return
        viewModelScope.launch {
            _isCheckingSecurity.value = true
            repository.performSecurityNetworkCheck()
                .onSuccess { report ->
                    _securityReport.value = report
                    _feedbackMessage.value = "بررسی امنیتی انجام شد: IP ${report.publicIp} (${report.tlsVersion})"
                }
                .onFailure { error ->
                    _feedbackMessage.value = "خطا در بررسی شبکه: ${error.localizedMessage ?: "اتصال برقرار نشد"}"
                }
            _isCheckingSecurity.value = false
        }
    }

    fun connectFastestServer(servers: List<VpnServer>, onFastestSelected: (VpnServer) -> Unit) {
        val activeServers = servers.filter { it.active }
        if (activeServers.isEmpty()) {
            _feedbackMessage.value = "هیچ سرور فعالی در Firebase یافت نشد."
            return
        }
        viewModelScope.launch {
            _isPingingAll.value = true
            var bestServer: VpnServer = activeServers.first()
            var bestRtt = Long.MAX_VALUE
            activeServers.forEach { server ->
                val rtt = repository.measureServerLatencyMs(server.host, server.port.toInt())
                val load = (rtt / 4L).coerceIn(10L, 68L)
                if (server.id.isNotBlank()) {
                    repository.updateServerLatency(server.id, rtt, load)
                }
                if (rtt < bestRtt) {
                    bestRtt = rtt
                    bestServer = server.copy(latencyMs = rtt)
                }
            }
            _isPingingAll.value = false
            _feedbackMessage.value = "سریع‌ترین سرور انتخاب شد: ${bestServer.name} (${bestRtt}ms)"
            onFastestSelected(bestServer)
        }
    }

    fun exportYamlString(servers: List<VpnServer>, settings: SystemSettings?): String {
        return YamlConfigCodec.exportToYaml(servers, settings)
    }

    fun exportSingleServerYaml(server: VpnServer): String {
        return YamlConfigCodec.exportSingleServerToYaml(server)
    }

    fun importServersFromYaml(yamlText: String) {
        viewModelScope.launch {
            val parsed = YamlConfigCodec.parseServersFromYaml(yamlText, currentUserId)
            if (parsed.isEmpty()) {
                _feedbackMessage.value = "هیچ سرور معتبری در فایل .yml یافت نشد."
                return@launch
            }
            repository.importServersBatch(parsed)
                .onSuccess { count ->
                    _feedbackMessage.value = "$count سرور از فایل .yml به Firebase اضافه شد."
                }
                .onFailure { error ->
                    _feedbackMessage.value = error.localizedMessage ?: "خطا در ورود فایل .yml"
                }
        }
    }

    fun selectServer(server: VpnServer, currentSettings: SystemSettings?) {
        val base = currentSettings ?: SystemSettings(ownerId = currentUserId)
        val updated = base.copy(selectedServerId = server.id)
        viewModelScope.launch {
            repository.saveSystemSettings(
                updated,
                auditNote = "انتخاب سرور پیش‌فرض: ${server.name}"
            ).onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "خطا در انتخاب سرور"
            }
        }
    }

    fun toggleBypassedPackage(settings: SystemSettings?, packageName: String, bypassed: Boolean) {
        val base = settings ?: SystemSettings(ownerId = currentUserId)
        val currentList = base.bypassedPackages.toMutableList()
        if (bypassed) {
            if (!currentList.contains(packageName) && currentList.size < 50) {
                currentList.add(packageName)
            }
        } else {
            currentList.remove(packageName)
        }
        val updated = base.copy(bypassedPackages = currentList)
        updateSystemSettings(
            updated,
            if (bypassed) "برنامه $packageName از تونل مستثنی شد" else "برنامه $packageName به تونل بازگشت"
        )
    }

    fun pingServer(server: VpnServer) {
        if (server.id.isBlank()) return
        viewModelScope.launch {
            _pingingServerIds.value = _pingingServerIds.value + server.id
            val rtt = repository.measureServerLatencyMs(server.host, server.port.toInt())
            val simulatedLoad = ((rtt / 4L).coerceIn(10L, 65L))
            repository.updateServerLatency(server.id, rtt, simulatedLoad)
                .onSuccess {
                    _feedbackMessage.value = "پینگ ${server.name}: ${rtt}ms"
                }
                .onFailure {
                    _feedbackMessage.value = it.localizedMessage ?: "خطا در ثبت پینگ سرور"
                }
            _pingingServerIds.value = _pingingServerIds.value - server.id
        }
    }

    fun pingAllServers(servers: List<VpnServer>) {
        if (servers.isEmpty() || _isPingingAll.value) return
        viewModelScope.launch {
            _isPingingAll.value = true
            servers.forEach { server ->
                if (server.id.isNotBlank()) {
                    _pingingServerIds.value = _pingingServerIds.value + server.id
                    val rtt = repository.measureServerLatencyMs(server.host, server.port.toInt())
                    val load = (rtt / 4L).coerceIn(10L, 68L)
                    repository.updateServerLatency(server.id, rtt, load)
                    _pingingServerIds.value = _pingingServerIds.value - server.id
                }
            }
            _isPingingAll.value = false
            _feedbackMessage.value = "تست پینگ تمام سرورهای Firebase به‌روزرسانی شد."
        }
    }

    fun saveServer(server: VpnServer, isEdit: Boolean) {
        viewModelScope.launch {
            val result = if (isEdit) {
                repository.updateServer(server)
            } else {
                repository.addServer(server)
            }
            result.onSuccess {
                _feedbackMessage.value = if (isEdit) {
                    "سرور ${server.name} در Firebase ویرایش شد."
                } else {
                    "سرور ${server.name} به Firebase اضافه شد."
                }
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "خطا در ذخیره سرور"
            }
        }
    }

    fun toggleServerActive(server: VpnServer, active: Boolean) {
        viewModelScope.launch {
            repository.toggleServerActive(server, active)
                .onSuccess {
                    _feedbackMessage.value = if (active) {
                        "سرور ${server.name} فعال شد."
                    } else {
                        "سرور ${server.name} غیرفعال شد."
                    }
                }
                .onFailure {
                    _feedbackMessage.value = it.localizedMessage ?: "خطا در تغییر وضعیت سرور"
                }
        }
    }

    fun deleteServer(server: VpnServer) {
        viewModelScope.launch {
            repository.deleteServer(server.id)
                .onSuccess {
                    _feedbackMessage.value = "سرور ${server.name} از Firebase حذف شد."
                }
                .onFailure {
                    _feedbackMessage.value = it.localizedMessage ?: "خطا در حذف سرور"
                }
        }
    }

    fun updateSystemSettings(updated: SystemSettings, auditNote: String) {
        viewModelScope.launch {
            repository.saveSystemSettings(updated, auditNote)
                .onSuccess {
                    _feedbackMessage.value = auditNote
                }
                .onFailure {
                    _feedbackMessage.value = it.localizedMessage ?: "خطا در ذخیره تنظیمات مالک"
                }
        }
    }

    fun onTunnelConnected(server: VpnServer, latencyMs: Long) {
        viewModelScope.launch {
            repository.logConnectionEvent(
                server = server,
                eventType = LogEventType.CONNECTED,
                durationSec = 0L,
                bytesTransferred = 0L,
                latencyMs = latencyMs,
                note = "اتصال امن برقرار شد (${server.protocol})"
            )
        }
    }

    fun onTunnelDisconnected(server: VpnServer?, durationSec: Long, bytesTotal: Long) {
        viewModelScope.launch {
            repository.logConnectionEvent(
                server = server,
                eventType = LogEventType.DISCONNECTED,
                durationSec = durationSec,
                bytesTransferred = bytesTotal,
                latencyMs = server?.latencyMs ?: 0L,
                note = "پایان نشست امن VPN"
            )
        }
    }

    private companion object {
        const val TAG = "VpnVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
