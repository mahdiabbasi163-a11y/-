package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface KodyarApiService {
    // 1️⃣ ورود و ثبت‌نام (Authentication / SMS OTP / Forgot Password)
    @POST("send-sms")
    suspend fun sendSms(
        @Body request: SendSmsRequest
    ): KodyarResponse

    @POST("auth/forgot-password-request")
    suspend fun forgotPasswordRequest(
        @Body request: ForgotPasswordRequestRequest
    ): KodyarResponse

    @POST("auth/forgot-password-reset")
    suspend fun forgotPasswordReset(
        @Body request: ForgotPasswordResetRequest
    ): KodyarResponse

    @POST("sms/send-otp")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): KodyarResponse

    @POST("auth/send-otp")
    suspend fun sendOtpAlt(
        @Body request: SendOtpRequest
    ): KodyarResponse

    @POST("sms/verify-otp")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): KodyarResponse

    @POST("auth/verify-otp")
    suspend fun verifyOtpAlt(
        @Body request: VerifyOtpRequest
    ): KodyarResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): KodyarResponse

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): KodyarResponse

    @GET("auth/me")
    suspend fun getMe(
        @Header("Authorization") token: String? = null
    ): KodyarResponse

    @POST("auth/update-profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String? = null,
        @Body request: UpdateProfileRequest
    ): KodyarResponse

    // 2️⃣ بانک کدهای خطا (Error Codes)
    @GET("error-codes")
    suspend fun getErrorCodes(
        @Query("search") search: String? = null,
        @Query("brand") brand: String? = null,
        @Query("device_type") deviceType: String? = null
    ): KodyarDatabaseResponse

    @GET("error-codes/search")
    suspend fun searchErrorCodesApi(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 1000
    ): KodyarDatabaseResponse

    @GET("error-codes/{id}")
    suspend fun getErrorCodeDetail(
        @Path("id") id: String
    ): Any

    // 3️⃣ مشکلات عمومی (General Problems)
    @GET("general-problems")
    suspend fun getGeneralProblems(
        @Query("search") search: String? = null
    ): KodyarDatabaseResponse

    @GET("problems")
    suspend fun getProblemsApi(): KodyarDatabaseResponse

    @GET("general-problems/{id}")
    suspend fun getGeneralProblemDetail(
        @Path("id") id: String
    ): Any

    // 4️⃣ فروشگاه و قطعات (Parts & Orders)
    @GET("parts")
    suspend fun getParts(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null
    ): KodyarDatabaseResponse

    @GET("spare-parts")
    suspend fun getSparePartsApi(): KodyarDatabaseResponse

    @POST("spare-parts")
    suspend fun addSparePartApi(
        @Header("Authorization") token: String? = null,
        @Body request: Any
    ): KodyarResponse

    @GET("store/parts")
    suspend fun getStorePartsApi(): KodyarDatabaseResponse

    @GET("technicians")
    suspend fun getTechniciansApi(): KodyarDatabaseResponse

    @GET("admin/technicians")
    suspend fun getAdminTechniciansApi(): KodyarDatabaseResponse

    @PUT("admin/technicians/{id}/status")
    suspend fun updateTechnicianStatusApi(
        @Header("Authorization") token: String? = null,
        @Path("id") id: String,
        @Body request: Any
    ): KodyarResponse

    @GET("parts/{id}")
    suspend fun getPartDetail(
        @Path("id") id: String
    ): Any

    @POST("orders")
    suspend fun createOrder(
        @Header("Authorization") token: String? = null,
        @Body request: Any
    ): KodyarResponse

    @POST("orders")
    suspend fun createRepairOrderApi(
        @Header("Authorization") token: String? = null,
        @Body request: RepairRequest
    ): KodyarResponse

    @GET("part-orders")
    suspend fun getPartOrdersApi(
        @Header("Authorization") token: String? = null
    ): KodyarResponse

    @POST("part-orders")
    suspend fun createPartOrderApi(
        @Header("Authorization") token: String? = null,
        @Body request: OrderPartRequest
    ): KodyarResponse

    @GET("orders")
    suspend fun getAllOrders(
        @Header("Authorization") token: String? = null
    ): KodyarResponse

    @GET("orders/my-orders")
    suspend fun getMyOrders(
        @Header("Authorization") token: String? = null
    ): KodyarResponse

    // 5️⃣ کارت به کارت، پرداخت و اشتراک‌ها (Payments & Subscriptions)
    @GET("subscriptions/plans")
    suspend fun getSubscriptionPlans(): KodyarPlansResponse

    @GET("subscription/plans")
    suspend fun getSubscriptionPlansLegacy(): KodyarPlansResponse

    @GET("settings/card-info")
    suspend fun getCardInfo(): CardInfoResponse

    @POST("payments/receipt")
    suspend fun submitReceipt(
        @Header("Authorization") token: String? = null,
        @Body request: ReceiptPaymentRequest
    ): KodyarResponse

    @GET("subscriptions/my-status")
    suspend fun getMySubscriptionStatus(
        @Header("Authorization") token: String? = null
    ): KodyarResponse

    // 🔄 پایگاه داده و تعمیرات
    @GET("get-database")
    suspend fun getDatabase(): KodyarDatabaseResponse

    @GET("get_database")
    suspend fun getDatabaseAlt1(): KodyarDatabaseResponse

    @GET("database")
    suspend fun getDatabaseAlt2(): KodyarDatabaseResponse

    @GET("repairs/list")
    suspend fun getRepairs(
        @Header("Authorization") token: String? = null,
        @Query("all") all: Boolean? = null
    ): KodyarResponse

    @POST("repairs/create")
    suspend fun createRepair(
        @Header("Authorization") token: String? = null,
        @Body request: RepairRequest
    ): KodyarResponse

    @POST("payment/card-verify")
    suspend fun verifyCard(
        @Header("Authorization") token: String? = null,
        @Body request: CardVerifyRequest
    ): KodyarResponse

    @GET("free/status")
    suspend fun getFreeStatus(
        @Header("Authorization") token: String? = null
    ): KodyarResponse

    @POST("free/use")
    suspend fun useFree(
        @Header("Authorization") token: String? = null,
        @Body request: FreeUseRequest
    ): KodyarResponse

    @POST("store/order")
    suspend fun storeOrder(
        @Header("Authorization") token: String? = null,
        @Body request: PurchasePartRequest
    ): KodyarResponse

    @POST("store/purchase")
    suspend fun purchasePart(
        @Header("Authorization") token: String? = null,
        @Body request: PurchasePartRequest
    ): KodyarResponse

    @POST("technician/orders/update")
    suspend fun updateOrderStatus(
        @Header("Authorization") token: String? = null,
        @Body request: OrderStatusUpdateRequest
    ): KodyarResponse

    @POST("orders/status")
    suspend fun updateOrderStatusAlt(
        @Header("Authorization") token: String? = null,
        @Body request: OrderStatusUpdateRequest
    ): KodyarResponse

    @PUT("orders/{id}/status")
    suspend fun updateOrderStatusPut(
        @Header("Authorization") token: String? = null,
        @Path("id") id: String,
        @Body request: OrderStatusUpdateRequest
    ): KodyarResponse

    @POST("directus-upload")
    suspend fun uploadFile(
        @Body request: UploadFileRequest
    ): UploadFileResponse

    @GET("store/my-orders")
    suspend fun getMyStoreOrders(
        @Header("Authorization") token: String? = null
    ): KodyarResponse
}

class FlexibleStringAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.NUMBER -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.STRING -> reader.nextString()
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: String?) {
        writer.value(value)
    }
}

class FlexibleBooleanAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Boolean? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NUMBER -> reader.nextInt() != 0
            JsonReader.Token.STRING -> {
                val str = reader.nextString().trim().lowercase()
                str == "true" || str == "1" || str == "yes" || str == "ok" || str == "approved"
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Boolean?) {
        writer.value(value)
    }
}

class FlexibleRepairOrderListAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): List<KodyarRepairOrder>? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                val list = mutableListOf<KodyarRepairOrder>()
                val orderAdapter = Moshi.Builder()
                    .add(FlexibleStringAdapter())
                    .add(FlexibleBooleanAdapter())
                    .addLast(KotlinJsonAdapterFactory())
                    .build()
                    .adapter(KodyarRepairOrder::class.java)
                while (reader.hasNext()) {
                    try {
                        orderAdapter.fromJson(reader)?.let { list.add(it) }
                    } catch (_: Exception) {
                        try { reader.skipValue() } catch (_: Exception) {}
                    }
                }
                reader.endArray()
                list
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                val orderAdapter = Moshi.Builder()
                    .add(FlexibleStringAdapter())
                    .add(FlexibleBooleanAdapter())
                    .addLast(KotlinJsonAdapterFactory())
                    .build()
                    .adapter(KodyarRepairOrder::class.java)
                val snapshot = reader.peekJson()
                try {
                    val singleOrder = orderAdapter.fromJson(snapshot)
                    reader.skipValue()
                    if (singleOrder != null && (!singleOrder.id.isNullOrBlank() || !singleOrder.description.isNullOrBlank() || !singleOrder.device_brand.isNullOrBlank())) {
                        listOf(singleOrder)
                    } else {
                        emptyList()
                    }
                } catch (_: Exception) {
                    try { reader.skipValue() } catch (_: Exception) {}
                    emptyList()
                }
            }
            else -> {
                try { reader.skipValue() } catch (_: Exception) {}
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: List<KodyarRepairOrder>?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.beginArray()
            val orderAdapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(KodyarRepairOrder::class.java)
            for (item in value) {
                orderAdapter.toJson(writer, item)
            }
            writer.endArray()
        }
    }
}

@JsonClass(generateAdapter = true)
data class SendSmsRequest(
    val phone: String,
    val type: String,
    val templateVars: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequestRequest(
    val phone: String,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordResetRequest(
    val phone: String,
    val code: String? = null,
    val otp: String? = null,
    val newPassword: String? = null,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class SendOtpRequest(val phone: String)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val phone: String,
    val code: String? = null,
    val otp: String? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val phone: String,
    val password: String,
    val username: String? = phone,
    val mobile: String? = phone
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val phone: String,
    val password: String,
    val full_name: String,
    val name: String? = full_name,
    val role: String? = null,
    val city: String? = null,
    @Json(name = "cityName") val cityName: String? = city,
    @Json(name = "city_name") val city_name: String? = city,
    @Json(name = "location") val location: String? = city,
    @Json(name = "activeLocation") val activeLocation: String? = city,
    @Json(name = "province") val province: String? = city,
    val categories: List<String>? = null,
    @Json(name = "specialty") val specialty: List<String>? = null,
    val documents: List<String>? = null,
    val document_images: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class OrderPartRequest(
    val part_id: Any,
    val quantity: Int = 1,
    val address: String? = null,
    val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class ReceiptPaymentRequest(
    val amount: Long,
    val tracking_code: String,
    val receipt_image: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class CardInfoData(
    val cardNumber: String? = null,
    val card_number: String? = null,
    val cardHolder: String? = null,
    val card_holder: String? = null,
    val bankName: String? = null,
    val bank_name: String? = null
)

@JsonClass(generateAdapter = true)
data class CardInfoResponse(
    val success: Boolean? = true,
    val status: String? = null,
    val cardNumber: String? = null,
    val card_number: String? = null,
    val cardHolder: String? = null,
    val card_holder: String? = null,
    val bankName: String? = null,
    val bank_name: String? = null,
    val message: String? = null,
    val data: CardInfoData? = null
)

@JsonClass(generateAdapter = true)
data class CardVerifyRequest(val card_holder: String, val track_number: String, val product_id: String)

@JsonClass(generateAdapter = true)
data class FreeUseRequest(val type: String)

object KodyarRetrofitClient {
    private val BASE_URL = com.example.BuildConfig.BASE_URL

    val siteRootUrl: String
        get() = BASE_URL.removeSuffix("/").removeSuffix("/api")

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val headerInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        val currentAuth = original.header("Authorization")
        val currentXSession = original.header("X-Session-Token")

        val tokenValue = currentAuth?.removePrefix("Bearer ")?.trim()
            ?: currentXSession
            ?: authToken

        if (!tokenValue.isNullOrBlank()) {
            val bearerHeader = if (tokenValue.startsWith("Bearer ", ignoreCase = true)) tokenValue else "Bearer $tokenValue"
            val rawToken = tokenValue.removePrefix("Bearer ").trim()
            builder.header("Authorization", bearerHeader)
            builder.header("X-Session-Token", rawToken)
        }

        chain.proceed(builder.build())
    }

    private val failoverInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val originalUrl = request.url
        val primaryHost = originalUrl.host

        val fallbackHosts = listOf("test.kodyar24.ir").distinct()
        val hostsToTry = listOf(primaryHost) + fallbackHosts.filter { it != primaryHost }

        var response: okhttp3.Response? = null
        var lastException: Exception? = null

        for (host in hostsToTry) {
            val newUrl = originalUrl.newBuilder().host(host).build()
            val newRequest = request.newBuilder().url(newUrl).build()
            try {
                val res = chain.proceed(newRequest)
                if (res.isSuccessful) {
                    return@Interceptor res
                } else {
                    response = res
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        response ?: throw (lastException ?: java.io.IOException("Unable to connect to Kodyar server"))
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(headerInterceptor)
        .addInterceptor(failoverInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val moshi = Moshi.Builder()
        .add(FlexibleStringAdapter())
        .add(FlexibleBooleanAdapter())
        .add(KodyarCityAdapter())
        .add(FlexibleRepairOrderListAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: KodyarApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(KodyarApiService::class.java)
    }
}
