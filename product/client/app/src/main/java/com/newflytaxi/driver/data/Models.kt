package com.newflytaxi.driver.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ---------------------------------------------------------------- envelope */

@Serializable
data class Envelope<T>(
    @SerialName("status_code") val statusCode: String,
    val message: String? = null,
    val data: T? = null,
)

/* ------------------------------------------------------------------ models */

@Serializable
data class Driver(
    val id: String,
    val name: String? = null,
    val tel: String? = null,
    @SerialName("car_number") val carNumber: String? = null,
    @SerialName("car_type") val carType: String? = null,
    @SerialName("car_group") val carGroup: String? = null,
    val status: Int? = null,
    val plan: Plan? = null,
    @SerialName("doc_verified") val docVerified: Boolean? = null,
)

@Serializable
data class Plan(val id: String? = null, val title: String? = null, val valid: Int? = null)

@Serializable
data class LoginResult(
    val id: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("login_key") val loginKey: String? = null,
    @SerialName("server_time") val serverTime: String? = null,
    val car_number: String? = null,
)

@Serializable
data class Order(
    val id: String,
    val status: String,
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("pickup_address") val pickupAddress: String? = null,
    @SerialName("pickup_lat") val pickupLat: Double? = null,
    @SerialName("pickup_lon") val pickupLon: Double? = null,
    @SerialName("dropoff1_address") val dropoffAddress: String? = null,
    @SerialName("dropoff1_lat") val dropoffLat: Double? = null,
    @SerialName("dropoff1_lon") val dropoffLon: Double? = null,
    val ct: String? = null,
    val distance: Int? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_car_number") val driverCarNumber: String? = null,
    val fare: Fare? = null,
)

@Serializable
data class Fare(
    @SerialName("amount_fare") val amountFare: Double = 0.0,
    @SerialName("amount_company_price") val amountCompanyPrice: Double = 0.0,
    @SerialName("amount_operation") val amountOperation: Double = 0.0,
    val summary: List<FareLine> = emptyList(),
)

@Serializable
data class FareLine(
    val title: String? = null,
    val charge: String? = null,
    @SerialName("amount_fare") val amountFare: Double = 0.0,
)

@Serializable
data class DriverConfig(
    @SerialName("dispatch_radius_m") val dispatchRadiusM: Int = 1500,
    @SerialName("accept_countdown_s") val acceptCountdownS: Int = 20,
    @SerialName("gps_idle_interval_s") val gpsIdleIntervalS: Int = 600,
    @SerialName("gps_on_order_interval_s") val gpsOnOrderIntervalS: Int = 20,
    @SerialName("cancel_fee_hkd") val cancelFeeHkd: Double = 6.0,
    @SerialName("arrive_pickup_max_radius_m") val arrivePickupMaxRadiusM: Int = 1000,
    @SerialName("arrive_dropoff_max_radius_m") val arriveDropoffMaxRadiusM: Int = 2000,
)

/* ---------------------------------------------------------------- requests */

@Serializable data class LoginRequest(val tel: String, val pwd: String, @SerialName("device_id") val deviceId: String, val app: String = "32", val v: String = "356")
@Serializable data class OnlineRequest(val online: Boolean)
@Serializable data class GpsRequest(val lat: Double, val lng: Double, val speed: Double = 0.0, val bid: String? = null)
@Serializable data class LatLngRequest(val lat: Double, val lng: Double)
@Serializable data class BidRequest(val bid: String)
@Serializable data class CompleteRequest(val bid: String, val tunnel: Boolean = false)
