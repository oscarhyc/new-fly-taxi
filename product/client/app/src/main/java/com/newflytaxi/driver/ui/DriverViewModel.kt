package com.newflytaxi.driver.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newflytaxi.driver.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriverUiState(
    val loggedIn: Boolean = false,
    val driver: Driver? = null,
    val online: Boolean = false,
    val orders: List<Order> = emptyList(),
    val current: Order? = null,
    val config: DriverConfig = DriverConfig(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class DriverViewModel(app: Application) : AndroidViewModel(app) {
    private val session = SessionStore(app)
    private val api = ApiFactory.create(session, BuildConfig.API_BASE_URL)

    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            if (session.token() != null) {
                _state.value = _state.value.copy(loggedIn = true)
                loadConfig()
            }
        }
    }

    fun login(tel: String, pwd: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        val r = api.login(LoginRequest(tel, pwd, session.deviceId()))
        if (r.statusCode == "200" && r.data != null) {
            session.saveToken(r.data.accessToken)
            _state.value = _state.value.copy(loggedIn = true, loading = false, message = "已登入")
            loadConfig()
        } else {
            _state.value = _state.value.copy(loading = false, error = r.message ?: "登入失敗 (${r.statusCode})")
        }
    }

    private suspend fun loadConfig() {
        runCatching { api.config() }.getOrNull()?.data?.let { cfg ->
            _state.value = _state.value.copy(config = cfg)
        }
    }

    fun setOnline(online: Boolean, lat: Double, lon: Double) = viewModelScope.launch {
        api.setOnline(OnlineRequest(online))
        _state.value = _state.value.copy(online = online)
        if (online) startPolling(lat, lon) else stopPolling()
    }

    /** Dispatch poll: the server pushes nothing, so the client polls like the observed app. */
    private fun startPolling(lat: Double, lon: Double) {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (true) {
                runCatching { api.available(LatLngRequest(lat, lon)) }.getOrNull()?.let { env ->
                    if (env.statusCode == "200") {
                        _state.value = _state.value.copy(orders = env.data ?: emptyList())
                    }
                }
                delay(5_000)
            }
        }
    }

    private fun stopPolling() { pollJob?.cancel(); pollJob = null }

    fun accept(order: Order) = viewModelScope.launch {
        val r = api.accept(BidRequest(order.id))
        handle(r, "已接單")
    }

    fun arrivedPickup() = advance { api.arrivedPickup(BidRequest(it)) }
    fun startRide() = advance { api.startRide(BidRequest(it)) }
    fun arrivedDropoff() = advance { api.arrivedDropoff(BidRequest(it)) }
    fun complete() = advance { api.complete(CompleteRequest(it)) }
    fun cancel() = advance { api.cancel(BidRequest(it)) }

    private fun advance(call: suspend (String) -> Envelope<Order>) = viewModelScope.launch {
        val bid = _state.value.current?.id ?: return@launch
        handle(call(bid), null)
    }

    private fun handle(r: Envelope<Order>, successMsg: String?) {
        if (r.statusCode == "200" && r.data != null) {
            _state.value = _state.value.copy(current = r.data, error = null, message = successMsg)
        } else {
            _state.value = _state.value.copy(error = r.message ?: "錯誤 ${r.statusCode}")
        }
    }
}
