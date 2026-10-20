package com.newflytaxi.driver.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

private val Context.dataStore by preferencesDataStore("new_fly_taxi_driver")

/* --------------------------------------------------------------- api (Ktor-agnostic HTTP) */

interface DriverApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Envelope<LoginResult>

    @GET("api/v1/config")
    suspend fun config(): Envelope<DriverConfig>

    @POST("api/v1/driver/online")
    suspend fun setOnline(@Body body: OnlineRequest): Envelope<Map<String, String>>

    @POST("api/v1/driver/gps")
    suspend fun gps(@Body body: GpsRequest): Envelope<Map<String, Any>>

    @POST("api/v1/orders/available")
    suspend fun available(@Body body: LatLngRequest): Envelope<List<Order>>

    @POST("api/v1/orders/accept")
    suspend fun accept(@Body body: BidRequest): Envelope<Order>

    @POST("api/v1/orders/arrived_pickup")
    suspend fun arrivedPickup(@Body body: BidRequest): Envelope<Order>

    @POST("api/v1/orders/start_ride")
    suspend fun startRide(@Body body: BidRequest): Envelope<Order>

    @POST("api/v1/orders/arrived_dropoff")
    suspend fun arrivedDropoff(@Body body: BidRequest): Envelope<Order>

    @POST("api/v1/orders/complete")
    suspend fun complete(@Body body: CompleteRequest): Envelope<Order>

    @POST("api/v1/orders/cancel")
    suspend fun cancel(@Body body: BidRequest): Envelope<Order>

    @GET("api/v1/orders/{bid}")
    suspend fun order(@Path("bid") bid: String): Envelope<Order>
}

/* --------------------------------------------------------------- session store */

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("access_token")
    private val deviceIdKey = stringPreferencesKey("device_id")

    suspend fun token(): String? = context.dataStore.data.first()[tokenKey]
    suspend fun deviceId(): String =
        context.dataStore.data.first()[deviceIdKey]
            ?: java.util.UUID.randomUUID().toString().also {
                context.dataStore.edit { p -> p[deviceIdKey] = it }
            }

    suspend fun saveToken(token: String) = context.dataStore.edit { it[tokenKey] = token }
    suspend fun clear() = context.dataStore.edit { it.remove(tokenKey) }
}

/* ------------------------------------------------------------------- factory */

object ApiFactory {
    fun create(session: SessionStore, baseUrl: String): DriverApi {
        val auth = Interceptor { chain ->
            val token = runBlocking { session.token() }
            val req = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                addHeader("Accept", "application/json")
            }.build()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DriverApi::class.java)
    }

    private fun runBlocking(block: suspend () -> String?) = kotlinx.coroutines.runBlocking { block() }
}
