package com.example.data

import com.example.BuildConfig
import com.example.data.api.ContentDto
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiApiService
import com.example.data.api.PartDto
import com.example.data.db.AssistantDao
import com.example.data.db.ConversationEntity
import com.example.data.db.MessageEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.db.CustomErrorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import com.example.data.db.OfflineDataDao
import com.example.data.db.toDomain
import com.example.data.db.toEntity

class AssistantRepository(
    private val assistantDao: AssistantDao,
    private val offlineDataDao: OfflineDataDao? = null,
    private val apiService: GeminiApiService
) {
    private val kodyarApiService = com.example.data.api.KodyarRetrofitClient.service

    fun getCachedErrorCodes(): Flow<List<com.example.data.db.ErrorCodeEntity>>? {
        return offlineDataDao?.getAllErrorCodes()
    }

    fun getCachedSpareParts(): Flow<List<com.example.data.db.SparePartEntity>>? {
        return offlineDataDao?.getAllSpareParts()
    }

    fun getCachedCommonProblems(): Flow<List<com.example.data.db.CommonProblemEntity>>? {
        return offlineDataDao?.getAllCommonProblems()
    }

    fun getCachedTechnicians(): Flow<List<com.example.data.db.TechnicianEntity>>? {
        return offlineDataDao?.getAllTechnicians()
    }

    suspend fun saveDatabaseToRoom(
        errorCodes: List<com.example.data.model.KodyarErrorCode>,
        spareParts: List<com.example.data.model.KodyarSparePart>,
        commonProblems: List<com.example.data.model.KodyarCommonProblem>,
        technicians: List<com.example.data.model.KodyarTechnician>
    ) = withContext(Dispatchers.IO) {
        val errEntities = errorCodes.map { it.toEntity() }
        val partEntities = spareParts.map { it.toEntity() }
        val probEntities = commonProblems.map { it.toEntity() }
        val techEntities = technicians.map { it.toEntity() }
        offlineDataDao?.updateAllOfflineData(errEntities, partEntities, probEntities, techEntities)
    }

    suspend fun clearAllOfflineCache() = withContext(Dispatchers.IO) {
        offlineDataDao?.clearAllOfflineCache()
    }

    suspend fun getKodyarDatabase(): com.example.data.model.KodyarDatabaseResponse = withContext(Dispatchers.IO) {
        var mergedErrorCodes = listOf<com.example.data.model.KodyarErrorCode>()
        var mergedSpareParts = listOf<com.example.data.model.KodyarSparePart>()
        var mergedProblems = listOf<com.example.data.model.KodyarCommonProblem>()
        var mergedTechs = listOf<com.example.data.model.KodyarTechnician>()

        // Tier 1: Try unified getDatabase
        try {
            val res = kodyarApiService.getDatabase()
            if (res.resolvedErrorCodes.isNotEmpty()) mergedErrorCodes = res.resolvedErrorCodes
            if (res.resolvedSpareParts.isNotEmpty()) mergedSpareParts = res.resolvedSpareParts
            if (res.resolvedCommonProblems.isNotEmpty()) mergedProblems = res.resolvedCommonProblems
            if (res.resolvedTechnicians.isNotEmpty()) mergedTechs = res.resolvedTechnicians

            if (mergedErrorCodes.isNotEmpty() && mergedSpareParts.isNotEmpty()) {
                return@withContext res
            }
        } catch (e: Exception) {
            android.util.Log.w("AssistantRepository", "getDatabase endpoint failed: ${e.message}")
        }

        // Tier 2: Fetch individual endpoints if missing
        if (mergedErrorCodes.isEmpty()) {
            try {
                val errRes = kodyarApiService.searchErrorCodesApi(limit = 1000)
                if (errRes.resolvedErrorCodes.isNotEmpty()) {
                    mergedErrorCodes = errRes.resolvedErrorCodes
                }
            } catch (e: Exception) {
                try {
                    val errRes2 = kodyarApiService.getErrorCodes()
                    if (errRes2.resolvedErrorCodes.isNotEmpty()) {
                        mergedErrorCodes = errRes2.resolvedErrorCodes
                    }
                } catch (_: Exception) {}
            }
        }

        if (mergedSpareParts.isEmpty()) {
            try {
                val partRes = kodyarApiService.getSparePartsApi()
                if (partRes.resolvedSpareParts.isNotEmpty()) {
                    mergedSpareParts = partRes.resolvedSpareParts
                }
            } catch (e: Exception) {
                try {
                    val partRes2 = kodyarApiService.getStorePartsApi()
                    if (partRes2.resolvedSpareParts.isNotEmpty()) {
                        mergedSpareParts = partRes2.resolvedSpareParts
                    }
                } catch (_: Exception) {}
            }
        }

        if (mergedProblems.isEmpty()) {
            try {
                val probRes = kodyarApiService.getProblemsApi()
                if (probRes.resolvedCommonProblems.isNotEmpty()) {
                    mergedProblems = probRes.resolvedCommonProblems
                }
            } catch (e: Exception) {
                try {
                    val probRes2 = kodyarApiService.getGeneralProblems()
                    if (probRes2.resolvedCommonProblems.isNotEmpty()) {
                        mergedProblems = probRes2.resolvedCommonProblems
                    }
                } catch (_: Exception) {}
            }
        }

        if (mergedTechs.isEmpty()) {
            try {
                val techRes = kodyarApiService.getTechniciansApi()
                if (techRes.resolvedTechnicians.isNotEmpty()) {
                    mergedTechs = techRes.resolvedTechnicians
                }
            } catch (e: Exception) {
                try {
                    val techRes2 = kodyarApiService.getAdminTechniciansApi()
                    if (techRes2.resolvedTechnicians.isNotEmpty()) {
                        mergedTechs = techRes2.resolvedTechnicians
                    }
                } catch (_: Exception) {}
            }
        }

        return@withContext com.example.data.model.KodyarDatabaseResponse(
            status = if (mergedErrorCodes.isNotEmpty() || mergedSpareParts.isNotEmpty() || mergedTechs.isNotEmpty() || mergedProblems.isNotEmpty()) "ok" else "empty",
            errorCodes = mergedErrorCodes,
            spareParts = mergedSpareParts,
            commonProblems = mergedProblems,
            technicians = mergedTechs
        )
    }

    suspend fun getSubscriptionPlans() = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getSubscriptionPlans()
        } catch (e: Exception) {
            com.example.data.model.KodyarPlansResponse(
                status = "error",
                plans = null
            )
        }
    }

    private fun parseApiError(e: Exception): String {
        if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val json = org.json.JSONObject(errorBody)
                    val msg = json.optString("error", json.optString("message", json.optString("detail", "")))
                    if (msg.isNotBlank()) return msg
                }
            } catch (_: Exception) {}
            return when (e.code()) {
                401 -> "اطلاعات ورود اشتباه است یا نشست شما منقضی شده."
                400 -> "درخواست نامعتبر است. اطلاعات وارد شده را بررسی کنید."
                403 -> "شما دسترسی لازم برای این عملیات را ندارید."
                404 -> "مسیر یا داده مورد نظر در سرور یافت نشد."
                422 -> "اطلاعات ورودی نامعتبر است."
                500, 502, 503 -> "خطای داخلی سرور (${e.code()}). لطفا کمی بعد تلاش کنید."
                else -> "خطای سرور: کد ${e.code()}"
            }
        }
        return "خطا در اتصال به سرور: ${e.localizedMessage ?: "لطفا اتصال اینترنت خود را بررسی کنید."}"
    }

    suspend fun sendSms(phone: String, type: String, verificationCode: String) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.api.SendSmsRequest(
                phone = phone,
                type = type,
                templateVars = mapOf("VERIFICATIONCODE" to verificationCode)
            )
            val res = kodyarApiService.sendSms(req)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "خطا در ارسال پیامک",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun forgotPasswordRequest(phone: String) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.api.ForgotPasswordRequestRequest(phone = phone)
            val res = kodyarApiService.forgotPasswordRequest(req)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                val otpMsg = if (!res.otp.isNullOrBlank()) "کد تایید صادر شد: ${res.otp}" else res.message ?: "کد تایید پیامک شد"
                res.copy(message = otpMsg)
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "شماره تلفن یافت نشد یا ثبت نشده است",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun forgotPasswordReset(phone: String, code: String, newPassword: String? = null) = withContext(Dispatchers.IO) {
        try {
            val req = com.example.data.api.ForgotPasswordResetRequest(
                phone = phone,
                code = code,
                otp = code,
                newPassword = newPassword
            )
            val res = kodyarApiService.forgotPasswordReset(req)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "کد تایید یا رمز جدید نامعتبر است",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun sendOtp(phone: String) = withContext(Dispatchers.IO) {
        try {
            val res = forgotPasswordRequest(phone)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                sendSms(phone, "otp", "1234")
            }
        } catch (e: Exception) {
            try {
                val resAlt = kodyarApiService.sendOtp(com.example.data.api.SendOtpRequest(phone))
                if (resAlt.status == "error" || (resAlt.success == false && resAlt.status != "ok" && resAlt.status != "success")) {
                    com.example.data.model.KodyarResponse(
                        status = "error",
                        error = resAlt.error ?: resAlt.message ?: "خطا در ارسال کد پیامک",
                        message = resAlt.message
                    )
                } else {
                    resAlt
                }
            } catch (e2: Exception) {
                val err = parseApiError(e)
                com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
            }
        }
    }

    suspend fun verifyOtp(phone: String, code: String, name: String? = null, newPassword: String? = null) = withContext(Dispatchers.IO) {
        try {
            val res = forgotPasswordReset(phone = phone, code = code, newPassword = newPassword)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                res
            } else {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = res.error ?: res.message ?: "کد تایید پیامک معتبر نیست",
                    message = res.message
                )
            }
        } catch (e: Exception) {
            try {
                val resAlt = kodyarApiService.verifyOtp(com.example.data.api.VerifyOtpRequest(phone = phone, code = code, otp = code, name = name))
                if (resAlt.status == "error" || (resAlt.success == false && resAlt.status != "ok" && resAlt.status != "success")) {
                    com.example.data.model.KodyarResponse(
                        status = "error",
                        error = resAlt.error ?: resAlt.message ?: "کد تایید پیامک معتبر نیست",
                        message = resAlt.message
                    )
                } else {
                    resAlt
                }
            } catch (e2: Exception) {
                val err = parseApiError(e)
                com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
            }
        }
    }

    suspend fun getCardInfo() = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getCardInfo()
        } catch (e: Exception) {
            com.example.data.api.CardInfoResponse(success = false, message = e.localizedMessage)
        }
    }

    suspend fun submitReceipt(token: String, amount: Long, trackingCode: String, receiptImage: String? = null, description: String? = null) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.submitReceipt(token, com.example.data.api.ReceiptPaymentRequest(amount, trackingCode, receiptImage, description))
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun createOrder(token: String, partId: Any, quantity: Int = 1, address: String? = null, phone: String? = null) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.createOrder(token, com.example.data.api.OrderPartRequest(partId, quantity, address, phone))
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(status = "error", error = err, message = err)
        }
    }

    suspend fun getMyOrders(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getMyOrders(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(status = "error", error = parseApiError(e))
        }
    }

    suspend fun getMySubscriptionStatus(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getMySubscriptionStatus(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(status = "error", error = parseApiError(e))
        }
    }

    suspend fun login(phone: String, pass: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.login(com.example.data.api.LoginRequest(phone, pass))
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = err,
                message = err
            )
        }
    }

    suspend fun register(
        phone: String,
        pass: String,
        name: String,
        role: String? = null,
        city: String? = null,
        district: String? = null,
        categories: List<String>? = null,
        documents: List<String>? = null,
        documentImages: List<String>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val effectiveAddress = if (!city.isNullOrBlank() && !district.isNullOrBlank() && !city.contains(district)) {
                "$city - $district"
            } else {
                city
            }
            kodyarApiService.register(
                com.example.data.api.RegisterRequest(
                    phone = phone,
                    password = pass,
                    full_name = name,
                    name = name,
                    fullName = name,
                    role = if (role == "technician") "technician" else "client",
                    city = city,
                    address = effectiveAddress,
                    full_address = effectiveAddress,
                    district = district,
                    region = district,
                    categories = categories,
                    specialty = categories,
                    specialties = categories,
                    documents = documents,
                    document_images = documentImages
                )
            )
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = err,
                message = err
            )
        }
    }

    suspend fun getMe(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getMe(token)
        } catch (e: Exception) {
            val err = parseApiError(e)
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = err,
                message = err
            )
        }
    }

    private fun normalizeIranianPhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digitsOnly = raw.map { ch ->
            when (ch) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        }.joinToString("").filter { it.isDigit() }

        return when {
            digitsOnly.startsWith("98") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(2)
            digitsOnly.startsWith("0098") && digitsOnly.length == 14 -> "0" + digitsOnly.substring(4)
            digitsOnly.startsWith("9") && digitsOnly.length == 10 -> "0$digitsOnly"
            digitsOnly.startsWith("09") && digitsOnly.length == 11 -> digitsOnly
            else -> digitsOnly.ifBlank { null }
        }
    }

    suspend fun getRepairs(token: String, all: Boolean? = null) = withContext(Dispatchers.IO) {
        try {
            val res = kodyarApiService.getRepairs(token, all)
            if (!res.repairs.isNullOrEmpty() || !res.orders.isNullOrEmpty() || !res.repair_requests.isNullOrEmpty() || !res.data.isNullOrEmpty()) {
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            val res2 = kodyarApiService.getMyOrders(token)
            if (!res2.repairs.isNullOrEmpty() || !res2.orders.isNullOrEmpty() || !res2.repair_requests.isNullOrEmpty() || !res2.data.isNullOrEmpty()) {
                return@withContext res2
            }
        } catch (_: Exception) {}

        try {
            kodyarApiService.getAllOrders(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در دریافت سفارش‌ها از سایت: ${e.message}",
                repairs = emptyList(),
                repair_requests = emptyList(),
                orders = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun getTechnicianOrders(token: String) = getRepairs(token, all = true)

    suspend fun createRepair(
        token: String,
        city: String,
        appliance: String,
        brand: String,
        model: String? = null,
        problemDescription: String,
        errorCode: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        address: String? = null,
        technicianId: String? = null,
        region: String? = null
    ): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val finalTechId = if (technicianId.isNullOrBlank()) null else technicianId
        val normalizedPhone = normalizeIranianPhone(customerPhone) ?: customerPhone
        val request = com.example.data.model.RepairRequest(
            customer_name = customerName,
            customerName = customerName,
            customer_phone = normalizedPhone,
            customerPhone = normalizedPhone,
            city = city,
            region = region,
            category = appliance,
            appliance = appliance,
            problem_description = problemDescription,
            description = problemDescription,
            address = address,
            brand = brand,
            model = model,
            error_code = errorCode,
            errorCode = errorCode,
            technician_id = finalTechId,
            technicianId = finalTechId
        )
        try {
            val res = kodyarApiService.createRepairOrderApi(token, request)
            if (res.status == "ok" || res.status == "success" || res.success == true) {
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            kodyarApiService.createRepair(token, request)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت درخواست در سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun verifyCard(token: String, cardHolder: String, trackNumber: String, productId: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.verifyCard(token, com.example.data.api.CardVerifyRequest(cardHolder, trackNumber, productId))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در تأیید کارت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun purchasePart(
        token: String,
        partId: String,
        partName: String,
        quantity: Int,
        unitPrice: Double,
        totalPrice: Double,
        address: String? = null,
        city: String? = null,
        notes: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        userPhone: String? = null
    ): com.example.data.model.KodyarResponse = withContext(Dispatchers.IO) {
        val phone = customerPhone ?: userPhone
        val normalizedPhone = normalizeIranianPhone(phone) ?: phone
        val request = com.example.data.model.PurchasePartRequest(
            customer_name = customerName,
            customerName = customerName,
            customer_phone = normalizedPhone,
            customerPhone = normalizedPhone,
            part_id = partId,
            partId = partId,
            part_name = partName,
            partName = partName,
            quantity = quantity,
            unit_price = unitPrice,
            unitPrice = unitPrice,
            total_price = totalPrice,
            totalPrice = totalPrice,
            address = address,
            city = city,
            notes = notes,
            user_phone = normalizedPhone,
            payment_method = "online"
        )
        try {
            val res = kodyarApiService.storeOrder(token, request)
            if (res.status == "ok" || res.status == "success" || res.success == true || !res.payment_url.isNullOrBlank() || !res.paymentUrl.isNullOrBlank()) {
                return@withContext res
            }
        } catch (_: Exception) {}

        try {
            kodyarApiService.purchasePart(token, request)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "error",
                user = null,
                error = "خطا در ثبت سفارش خرید در سایت: ${e.message}",
                repairs = emptyList(),
                data = emptyList(),
                error_count = null,
                problem_count = null
            )
        }
    }

    suspend fun updateOrderStatus(token: String, orderId: String, status: String): com.example.data.model.KodyarResponse =
        withContext(Dispatchers.IO) {
            val req = com.example.data.model.OrderStatusUpdateRequest(order_id = orderId, orderId = orderId, status = status)
            try {
                val res = kodyarApiService.updateOrderStatus(token, req)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    return@withContext res
                }
            } catch (_: Exception) {}

            try {
                kodyarApiService.updateOrderStatusAlt(token, req)
            } catch (e: Exception) {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = parseApiError(e)
                )
            }
        }

    suspend fun updateProfile(token: String, fullName: String?, city: String?) =
        withContext(Dispatchers.IO) {
            kodyarApiService.updateProfile(token, com.example.data.model.UpdateProfileRequest(fullName, city))
        }

    suspend fun uploadFile(name: String, base64Data: String) =
        withContext(Dispatchers.IO) {
            kodyarApiService.uploadFile(com.example.data.model.UploadFileRequest(name, base64Data))
        }

    suspend fun getMyStoreOrders(token: String) =
        withContext(Dispatchers.IO) {
            try {
                kodyarApiService.getMyStoreOrders(token)
            } catch (e: Exception) {
                com.example.data.model.KodyarResponse(
                    status = "error",
                    error = e.message
                )
            }
        }

    suspend fun getFreeStatus(token: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.getFreeStatus(token)
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "ok",
                user = null,
                error = null,
                repairs = null,
                data = null,
                error_count = 5,
                problem_count = 5
            )
        }
    }

    suspend fun useFree(token: String, type: String) = withContext(Dispatchers.IO) {
        try {
            kodyarApiService.useFree(token, com.example.data.api.FreeUseRequest(type))
        } catch (e: Exception) {
            com.example.data.model.KodyarResponse(
                status = "ok",
                user = null,
                error = null,
                repairs = null,
                data = null,
                error_count = 5,
                problem_count = 5
            )
        }
    }

    val allConversations: Flow<List<ConversationEntity>> = assistantDao.getAllConversations()

    fun getMessages(conversationId: Long): Flow<List<MessageEntity>> =
        assistantDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(title: String, personaName: String): Long = withContext(Dispatchers.IO) {
        val conversation = ConversationEntity(title = title, personaName = personaName)
        assistantDao.insertConversation(conversation)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        assistantDao.deleteConversationById(id)
    }

    suspend fun updateConversationTitle(id: Long, title: String) = withContext(Dispatchers.IO) {
        assistantDao.updateConversationTitle(id, title)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        assistantDao.clearAllConversations()
    }

    // Saved Errors (Bookmarks)
    val allSavedErrors: Flow<List<SavedErrorEntity>> = assistantDao.getAllSavedErrors()

    suspend fun toggleSavedError(code: String, brand: String, category: String, isCurrentlySaved: Boolean) = withContext(Dispatchers.IO) {
        if (isCurrentlySaved) {
            assistantDao.deleteSavedError(code, brand, category)
        } else {
            assistantDao.insertSavedError(
                SavedErrorEntity(code = code, brand = brand, category = category)
            )
        }
    }

    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean> =
        assistantDao.isErrorSaved(code, brand, category)

    // Custom Errors
    val allCustomErrors: Flow<List<CustomErrorEntity>> = assistantDao.getAllCustomErrors()

    suspend fun addCustomError(customError: CustomErrorEntity) = withContext(Dispatchers.IO) {
        assistantDao.insertCustomError(customError)
    }

    suspend fun deleteCustomError(id: Long) = withContext(Dispatchers.IO) {
        assistantDao.deleteCustomError(id)
    }

    suspend fun updateCustomErrorNote(id: Long, note: String) = withContext(Dispatchers.IO) {
        assistantDao.updateCustomErrorNote(id, note)
    }

    suspend fun sendMessage(
        conversationId: Long,
        userText: String,
        systemInstructionText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Insert user message in database
            val userMessage = MessageEntity(
                conversationId = conversationId,
                role = "user",
                text = userText
            )
            assistantDao.insertMessage(userMessage)

            // 2. Fetch full history to send as context to Gemini
            val history = assistantDao.getMessagesList(conversationId)
            val contents = history.map { msg ->
                ContentDto(
                    role = msg.role,
                    parts = listOf(PartDto(text = msg.text))
                )
            }

            // 3. Try Kodyar24 Server AI Chat endpoint first (/api/chat)
            try {
                val chatReq = com.example.data.model.KodyarChatRequest(
                    message = userText,
                    device_type = "لوازم خانگی",
                    brand = "عمومی",
                    history = history.map { mapOf("role" to it.role, "content" to it.text) }
                )
                val chatRes = kodyarApiService.sendAiChat(request = chatReq)
                val replyText = chatRes.reply
                if (!replyText.isNullOrBlank()) {
                    val modelMessage = MessageEntity(
                        conversationId = conversationId,
                        role = "model",
                        text = replyText
                    )
                    assistantDao.insertMessage(modelMessage)
                    return@withContext Result.success(replyText)
                }
            } catch (e: Exception) {
                android.util.Log.w("AssistantRepository", "Kodyar chat API attempt: ${e.message}, falling back to Gemini API")
            }

            // 4. Prepare system instruction & fallback to Gemini API
            val systemInstruction = if (systemInstructionText.isNotEmpty()) {
                ContentDto(parts = listOf(PartDto(text = systemInstructionText)))
            } else {
                null
            }

            // 5. Check if API key is configured
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("پاسخی از سرور دریافت نشد. لطفاً اتصال اینترنت خود را بررسی کنید."))
            }

            // 6. Send API request to Gemini
            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )
            val response = apiService.generateContent(apiKey, request)

            // 7. Parse response & insert model reply
            val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (replyText != null) {
                val modelMessage = MessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    text = replyText
                )
                assistantDao.insertMessage(modelMessage)
                Result.success(replyText)
            } else {
                Result.failure(Exception("پاسخی از هوش مصنوعی دریافت نشد."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
