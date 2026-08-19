package com.example.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Json
@JsonClass(generateAdapter = true)
data class KodyarDatabaseData(
    val errorCodes: List<KodyarErrorCode>? = null,
    @Json(name = "error_codes") val error_codes: List<KodyarErrorCode>? = null,
    val spareParts: List<KodyarSparePart>? = null,
    @Json(name = "spare_parts") val spare_parts: List<KodyarSparePart>? = null,
    val technicians: List<KodyarTechnician>? = null,
    @Json(name = "techs") val techs: List<KodyarTechnician>? = null,
    val users: List<KodyarUser>? = null,
    val commonProblems: List<KodyarCommonProblem>? = null,
    @Json(name = "common_problems") val common_problems: List<KodyarCommonProblem>? = null,
    val categoriesList: List<String>? = null,
    @Json(name = "categories_list") val categories_list: List<String>? = null,
    val brandsList: List<String>? = null,
    @Json(name = "brands_list") val brands_list: List<String>? = null,
    val citiesList: List<KodyarCity>? = null,
    @Json(name = "cities_list") val cities_list: List<KodyarCity>? = null
)

@JsonClass(generateAdapter = true)
data class KodyarDatabaseResponse(
    val status: String? = null,
    val data: KodyarDatabaseData? = null,
    val errorCodes: List<KodyarErrorCode>? = null,
    @Json(name = "error_codes") val error_codes: List<KodyarErrorCode>? = null,
    val spareParts: List<KodyarSparePart>? = null,
    @Json(name = "spare_parts") val spare_parts: List<KodyarSparePart>? = null,
    val technicians: List<KodyarTechnician>? = null,
    @Json(name = "techs") val techs: List<KodyarTechnician>? = null,
    val users: List<KodyarUser>? = null,
    val commonProblems: List<KodyarCommonProblem>? = null,
    @Json(name = "common_problems") val common_problems: List<KodyarCommonProblem>? = null,
    val categoriesList: List<String>? = null,
    @Json(name = "categories_list") val categories_list: List<String>? = null,
    val brandsList: List<String>? = null,
    @Json(name = "brands_list") val brands_list: List<String>? = null,
    val citiesList: List<KodyarCity>? = null,
    @Json(name = "cities_list") val cities_list: List<KodyarCity>? = null,
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val isForceUpdate: Boolean? = null,
    val updateNotes: List<String>? = null
) {
    val resolvedErrorCodes: List<KodyarErrorCode>
        get() = errorCodes ?: error_codes ?: data?.errorCodes ?: data?.error_codes ?: emptyList()

    val resolvedSpareParts: List<KodyarSparePart>
        get() = spareParts ?: spare_parts ?: data?.spareParts ?: data?.spare_parts ?: emptyList()

    val resolvedTechnicians: List<KodyarTechnician>
        get() {
            val directList = if (!technicians.isNullOrEmpty()) technicians else if (!techs.isNullOrEmpty()) techs else if (data?.technicians?.isNotEmpty() == true) data.technicians else if (data?.techs?.isNotEmpty() == true) data.techs else null
            val combined = mutableListOf<KodyarTechnician>()
            if (!directList.isNullOrEmpty()) {
                combined.addAll(directList)
            }
            val allUsers = users ?: data?.users
            val techUsers = allUsers?.filter { it.role == "technician" || it.role == "tech" || it.role == "repairman" }
            if (!techUsers.isNullOrEmpty()) {
                for (u in techUsers) {
                    val exists = combined.any { it.id == u.id || (!it.name.isNullOrBlank() && it.name == u.full_name) }
                    if (!exists) {
                        combined.add(
                            KodyarTechnician(
                                id = u.id,
                                name = u.full_name.ifBlank { "تکنسین کدیار" },
                                city = u.resolvedCity,
                                isVerified = u.isApprovedUser,
                                completedOrders = 0,
                                bio = "تکنسین متخصص کدیار۲۴",
                                categories = u.categories ?: u.specialty ?: listOf("لوازم خانگی"),
                                rating = 5.0,
                                satisfactionRate = 100,
                                image = u.resolvedAvatarUrl,
                                imageUrl = u.resolvedAvatarUrl
                            )
                        )
                    }
                }
            }
            return combined.map { t ->
                t.copy(
                    name = t.resolvedName,
                    city = t.resolvedCity,
                    categories = t.resolvedCategories,
                    isVerified = t.resolvedIsVerified
                )
            }
        }

    val resolvedCommonProblems: List<KodyarCommonProblem>
        get() = commonProblems ?: common_problems ?: data?.commonProblems ?: data?.common_problems ?: emptyList()

    val resolvedCategoriesList: List<String>
        get() = categoriesList ?: categories_list ?: data?.categoriesList ?: data?.categories_list ?: emptyList()

    val resolvedBrandsList: List<String>
        get() = brandsList ?: brands_list ?: data?.brandsList ?: data?.brands_list ?: emptyList()

    val resolvedCitiesList: List<KodyarCity>
        get() = citiesList ?: cities_list ?: data?.citiesList ?: data?.cities_list ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class KodyarCity(
    val name: String? = null,
    val title: String? = null,
    val city: String? = null,
    val cityName: String? = null,
    val name_fa: String? = null,
    val nameFarsi: String? = null,
    val slug: String? = null,
    val regions: List<String>? = null
)

class KodyarCityAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): KodyarCity? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }
        if (reader.peek() == JsonReader.Token.STRING) {
            val nameValue = reader.nextString()
            return KodyarCity(name = nameValue)
        }
        reader.beginObject()
        var name: String? = null
        var title: String? = null
        var city: String? = null
        var cityName: String? = null
        var name_fa: String? = null
        var nameFarsi: String? = null
        var slug: String? = null
        var regions: MutableList<String>? = null

        val options = JsonReader.Options.of("name", "title", "city", "cityName", "name_fa", "nameFarsi", "slug", "regions", "regions_list")
        while (reader.hasNext()) {
            when (reader.selectName(options)) {
                0 -> name = reader.nextString()
                1 -> title = reader.nextString()
                2 -> city = reader.nextString()
                3 -> cityName = reader.nextString()
                4 -> name_fa = reader.nextString()
                5 -> nameFarsi = reader.nextString()
                6 -> slug = reader.nextString()
                7, 8 -> {
                    if (reader.peek() == JsonReader.Token.NULL) {
                        reader.nextNull<Unit>()
                    } else {
                        val list = mutableListOf<String>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonReader.Token.STRING) {
                                list.add(reader.nextString())
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                        regions = list
                    }
                }
                else -> {
                    reader.skipName()
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
        return KodyarCity(name, title, city, cityName, name_fa, nameFarsi, slug, regions)
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: KodyarCity?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        if (value.name != null) writer.name("name").value(value.name)
        if (value.title != null) writer.name("title").value(value.title)
        if (value.city != null) writer.name("city").value(value.city)
        if (value.cityName != null) writer.name("cityName").value(value.cityName)
        if (value.name_fa != null) writer.name("name_fa").value(value.name_fa)
        if (value.nameFarsi != null) writer.name("nameFarsi").value(value.nameFarsi)
        if (value.slug != null) writer.name("slug").value(value.slug)
        if (value.regions != null) {
            writer.name("regions")
            writer.beginArray()
            for (r in value.regions) writer.value(r)
            writer.endArray()
        }
        writer.endObject()
    }
}

@JsonClass(generateAdapter = true)
data class KodyarErrorCode(
    val id: String? = null,
    val code: String? = null,
    @Json(name = "error_code") val error_code: String? = null,
    val brand: String? = null,
    val category: String? = null,
    @Json(name = "device_type") val device_type: String? = null,
    val title: String? = null,
    @Json(name = "error_title") val error_title: String? = null,
    val description: String? = null,
    val causes: Any? = null,
    val steps: Any? = null,
    val solutions: Any? = null,
    val hazardLevel: String? = null,
    @Json(name = "hazard_level") val hazard_level: String? = null,
    val videoUrl: String? = null,
    @Json(name = "video_url") val video_url: String? = null,
    val isApproved: Boolean? = null,
    @Json(name = "is_approved") val is_approved: Boolean? = null,
    val model: String? = null
) {
    val resolvedCode: String
        get() = (code ?: error_code ?: id ?: "").ifBlank { "N/A" }

    val resolvedCategory: String
        get() = (category ?: device_type ?: "").ifBlank { "عمومی" }

    val resolvedTitle: String
        get() = (title ?: error_title ?: description ?: "").ifBlank { "کد خطا $resolvedCode" }

    val resolvedSteps: Any?
        get() = steps ?: solutions
}

@JsonClass(generateAdapter = true)
data class KodyarSparePart(
    val id: String? = null,
    val name: String? = null,
    val brand: String? = null,
    @Json(name = "compatible_brands") val compatible_brands: Any? = null,
    val category: String? = null,
    @Json(name = "device_category") val device_category: String? = null,
    @Json(name = "device_type") val device_type: String? = null,
    val model: String? = null,
    @Json(name = "device_model") val device_model: String? = null,
    val price: Double? = null,
    val stock: Int? = null,
    val image: String? = null,
    val imageUrl: String? = null,
    @Json(name = "image_url") val image_url: String? = null,
    val description: String? = null,
    @Json(name = "short_description") val short_description: String? = null,
    @Json(name = "technical_description") val technical_description: String? = null
) {
    val resolvedCategory: String
        get() = (device_category ?: category ?: device_type ?: "").trim()

    val resolvedBrand: String
        get() {
            if (!brand.isNullOrBlank()) return brand.trim()
            if (compatible_brands is List<*>) {
                return compatible_brands.filterNotNull().joinToString("، ")
            } else if (compatible_brands is String && compatible_brands.isNotBlank()) {
                return compatible_brands.trim()
            }
            return ""
        }

    val resolvedModel: String
        get() = (model ?: device_model ?: "").trim()

    val resolvedDescription: String
        get() = (short_description ?: technical_description ?: description ?: "").trim()

    val deviceAndBrandSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (resolvedCategory.isNotBlank()) parts.add(resolvedCategory)
            if (resolvedBrand.isNotBlank()) parts.add(resolvedBrand)
            if (resolvedModel.isNotBlank()) parts.add("مدل $resolvedModel")
            return parts.joinToString(" • ")
        }
}

@JsonClass(generateAdapter = true)
data class KodyarTechnician(
    val id: String? = null,
    val user_id: String? = null,
    val name: String? = null,
    @Json(name = "full_name") val full_name: String? = null,
    val phone: String? = null,
    val city: String? = null,
    @Json(name = "cityName") val cityName: String? = null,
    @Json(name = "city_name") val city_name: String? = null,
    @Json(name = "activeLocation") val activeLocation: String? = null,
    @Json(name = "active_location") val active_location: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "address") val address: String? = null,
    val isVerified: Any? = null,
    @Json(name = "is_verified") val is_verified: Any? = null,
    @Json(name = "is_approved") val is_approved: Any? = null,
    val completedOrders: Int? = null,
    @Json(name = "completed_orders") val completed_orders: Int? = null,
    val bio: String? = null,
    val categories: Any? = null,
    @Json(name = "specialty") val specialty: Any? = null,
    @Json(name = "specialties") val specialties: Any? = null,
    val rating: Double? = 5.0,
    val satisfactionRate: Int? = 100,
    @Json(name = "satisfaction_rate") val satisfaction_rate: Int? = 100,
    val image: String? = null,
    val imageUrl: String? = null,
    @Json(name = "image_url") val image_url: String? = null,
    @Json(name = "profile_image") val profile_image: String? = null,
    @Json(name = "profile_image_url") val profile_image_url: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatar_url: String? = null,
    @Json(name = "photo") val photo: String? = null,
    @Json(name = "picture") val picture: String? = null,
    @Json(name = "user_avatar") val user_avatar: String? = null,
    @Json(name = "user_image") val user_image: String? = null,
    val documents: Any? = null,
    val document_images: Any? = null,
    val uploaded_documents: Any? = null,
    val status: String? = null
) {
    val resolvedName: String
        get() = (name ?: full_name ?: "").ifBlank { "تکنسین کدیار" }

    val resolvedCity: String
        get() = listOfNotNull(city, cityName, city_name, activeLocation, active_location, location, province, address)
            .firstOrNull { it.isNotBlank() } ?: "اراک"

    val resolvedCategories: List<String>
        get() {
            fun parseCatList(input: Any?): List<String>? {
                if (input == null) return null
                if (input is List<*>) {
                    val list = input.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() && it != "[]" }
                    if (list.isNotEmpty()) return list
                }
                if (input is String && input.isNotBlank()) {
                    val trimmed = input.trim()
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        val inner = trimmed.substring(1, trimmed.length - 1)
                        val items = inner.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }.filter { it.isNotBlank() }
                        if (items.isNotEmpty()) return items
                    }
                    if (trimmed != "[]" && trimmed != "null") {
                        return listOf(trimmed)
                    }
                }
                return null
            }
            return parseCatList(specialties)
                ?: parseCatList(categories)
                ?: parseCatList(specialty)
                ?: listOf("لوازم خانگی")
        }

    val resolvedIsVerified: Boolean
        get() = true

    val resolvedAvatarUrl: String?
        get() {
            val candidate = listOfNotNull(
                image, imageUrl, image_url, profile_image, profile_image_url,
                avatar, avatar_url, photo, picture, user_avatar, user_image,
                (uploaded_documents as? List<*>)?.firstOrNull()?.toString(),
                (document_images as? List<*>)?.firstOrNull()?.toString(),
                (documents as? List<*>)?.firstOrNull()?.toString()
            ).firstOrNull { it.isNotBlank() } ?: return null

            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            val trimmed = candidate.trim()
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/") -> trimmed
                trimmed.startsWith("/") -> "$baseUrl$trimmed"
                else -> "$baseUrl/$trimmed"
            }
        }
}

@JsonClass(generateAdapter = true)
data class KodyarCommonProblem(
    val id: String? = null,
    val title: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val description: String? = null,
    val causes: Any? = null,
    val steps: Any? = null
)

@JsonClass(generateAdapter = true)
data class KodyarUser(
    val id: String = "",
    val full_name: String = "",
    val phone: String = "",
    val subscription: KodyarSubscription? = null,
    val is_premium: Boolean? = null,
    val expiry_date: String? = null,
    val subscription_expiry: String? = null,
    val role: String? = "customer",
    val city: String? = null,
    @Json(name = "cityName") val cityName: String? = null,
    @Json(name = "city_name") val city_name: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "activeLocation") val activeLocation: String? = null,
    @Json(name = "province") val province: String? = null,
    @Json(name = "address") val address: String? = null,
    val categories: List<String>? = null,
    @Json(name = "specialty") val specialty: List<String>? = null,
    val district: String? = null,
    val is_approved: Boolean? = true,
    val approval_status: String? = "approved",
    val uploaded_documents: List<String>? = null,
    val status: String? = null,
    val is_verified: Boolean? = null,
    val isVerified: Boolean? = null,
    val image: String? = null,
    val imageUrl: String? = null,
    @Json(name = "image_url") val image_url: String? = null,
    @Json(name = "profile_image") val profile_image: String? = null,
    @Json(name = "profile_image_url") val profile_image_url: String? = null,
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatar_url: String? = null,
    @Json(name = "photo") val photo: String? = null,
    @Json(name = "picture") val picture: String? = null,
    @Json(name = "user_avatar") val user_avatar: String? = null,
    @Json(name = "user_image") val user_image: String? = null,
    val documents: List<String>? = null,
    val document_images: List<String>? = null
) {
    val resolvedCity: String
        get() = listOfNotNull(city, cityName, city_name, activeLocation, location, province, address, district)
            .firstOrNull { it.isNotBlank() } ?: "اراک"

    val isApprovedUser: Boolean
        get() {
            if (role != "technician" && role != "tech" && role != "repairman") return true
            if (is_approved == true || is_verified == true || isVerified == true) return true
            val stat = (status ?: approval_status ?: "").trim().lowercase()
            if (stat == "rejected" || is_approved == false) return false
            return true
        }

    val resolvedAvatarUrl: String?
        get() {
            val candidate = listOfNotNull(
                image, imageUrl, image_url, profile_image, profile_image_url,
                avatar, avatar_url, photo, picture, user_avatar, user_image,
                uploaded_documents?.firstOrNull(),
                document_images?.firstOrNull(),
                documents?.firstOrNull()
            ).firstOrNull { it.isNotBlank() } ?: return null

            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            val trimmed = candidate.trim()
            return when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/") -> trimmed
                trimmed.startsWith("/") -> "$baseUrl$trimmed"
                else -> "$baseUrl/$trimmed"
            }
        }
}

@JsonClass(generateAdapter = true)
data class KodyarSubscription(
    val is_premium: Boolean = false,
    val expiry_date: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarResponse(
    val status: String? = "ok",
    val success: Boolean? = null,
    val token: String? = null,
    val session_token: String? = null,
    val user: KodyarUser? = null,
    val subscription: KodyarSubscription? = null,
    val error: String? = null,
    val message: String? = null,
    val otp: String? = null,
    val order_id: Any? = null,
    val orderId: Any? = null,
    val payment_url: String? = null,
    val paymentUrl: String? = null,
    val shamsi_date: String? = null,
    val shamsiDate: String? = null,
    val repairs: List<KodyarRepairOrder>? = null,
    val repair_requests: List<KodyarRepairOrder>? = null,
    val orders: List<KodyarRepairOrder>? = null,
    val data: List<KodyarRepairOrder>? = null,
    val error_count: Int? = null,
    val problem_count: Int? = null
)

@JsonClass(generateAdapter = true)
data class KodyarRepairOrder(
    val id: String? = null,
    val order_id: String? = null,
    val orderId: String? = null,
    val user_id: String? = null,
    val user_name: String? = null,
    val user_phone: String? = null,
    val customer_name: String? = null,
    val customerName: String? = null,
    val customer_phone: String? = null,
    val customerPhone: String? = null,
    val technician_id: String? = null,
    val technicianId: String? = null,
    val technician_name: String? = null,
    val technicianName: String? = null,
    val device_brand: String? = null,
    val brand: String? = null,
    val device_category: String? = null,
    val category: String? = null,
    val appliance: String? = null,
    val model: String? = null,
    val error_code: String? = null,
    val errorCode: String? = null,
    val description: String? = null,
    val problem_description: String? = null,
    val problemDescription: String? = null,
    val city: String? = null,
    val region: String? = null,
    val address: String? = null,
    val status: String? = null, // pending, accepted, ongoing, completed, rejected
    val created_at: String? = null,
    val shamsi_date: String? = null,
    val shamsiDate: String? = null
) {
    val resolvedCustomerName: String
        get() = listOfNotNull(customer_name, customerName, user_name).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedCustomerPhone: String
        get() = listOfNotNull(customer_phone, customerPhone, user_phone).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedDescription: String
        get() = listOfNotNull(description, problem_description, problemDescription).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedCategory: String
        get() = listOfNotNull(category, device_category, appliance).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedBrand: String
        get() = listOfNotNull(brand, device_brand).firstOrNull { it.isNotBlank() } ?: ""

    val resolvedDate: String
        get() = listOfNotNull(shamsi_date, shamsiDate, created_at).firstOrNull { it.isNotBlank() } ?: ""
}

@JsonClass(generateAdapter = true)
data class KodyarSubscriptionPlan(
    val id: String = "",
    val name: String = "",
    val duration_days: Int = 0,
    val price: Double = 0.0,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarPlansResponse(
    val status: String = "ok",
    val plans: List<KodyarSubscriptionPlan>? = null
)

@JsonClass(generateAdapter = true)
data class PartPurchaseOrder(
    val id: String = "",
    val partId: String = "",
    val partName: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val address: String = "",
    val notes: String = "",
    val dateStr: String = "",
    val status: String = "pending", // pending, sent, delivered, completed
    val paymentUrl: String? = null,
    val shamsiDate: String? = null
)

@JsonClass(generateAdapter = true)
data class RepairRequest(
    @Json(name = "customer_name") val customer_name: String? = null,
    @Json(name = "customerName") val customerName: String? = null,
    @Json(name = "customer_phone") val customer_phone: String? = null,
    @Json(name = "customerPhone") val customerPhone: String? = null,
    val city: String = "",
    val region: String? = null,
    val category: String? = null,
    val appliance: String? = null,
    @Json(name = "problem_description") val problem_description: String? = null,
    @Json(name = "description") val description: String? = null,
    val address: String? = null,
    val brand: String = "",
    val model: String? = null,
    @Json(name = "error_code") val error_code: String? = null,
    @Json(name = "errorCode") val errorCode: String? = null,
    @Json(name = "technician_id") val technician_id: String? = null,
    @Json(name = "technicianId") val technicianId: String? = null
)

@JsonClass(generateAdapter = true)
data class PurchasePartRequest(
    @Json(name = "customer_name") val customer_name: String? = null,
    @Json(name = "customerName") val customerName: String? = null,
    @Json(name = "customer_phone") val customer_phone: String? = null,
    @Json(name = "customerPhone") val customerPhone: String? = null,
    @Json(name = "part_id") val part_id: String = "",
    @Json(name = "partId") val partId: String = "",
    @Json(name = "part_name") val part_name: String = "",
    @Json(name = "partName") val partName: String = "",
    val quantity: Int = 1,
    @Json(name = "unit_price") val unit_price: Double = 0.0,
    @Json(name = "unitPrice") val unitPrice: Double = 0.0,
    @Json(name = "total_price") val total_price: Double = 0.0,
    @Json(name = "totalPrice") val totalPrice: Double = 0.0,
    val address: String? = null,
    val city: String? = null,
    val notes: String? = null,
    @Json(name = "user_phone") val user_phone: String? = null,
    @Json(name = "payment_method") val payment_method: String? = "online",
    @Json(name = "payment_type") val payment_type: String? = "part_purchase",
    @Json(name = "type") val type: String? = "part_purchase",
    @Json(name = "status") val status: String? = "pending"
)

@JsonClass(generateAdapter = true)
data class OrderStatusUpdateRequest(
    @Json(name = "order_id") val order_id: String = "",
    @Json(name = "orderId") val orderId: String? = null,
    val status: String = "" // accepted | ongoing | completed | rejected
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    @Json(name = "full_name") val fullName: String? = null,
    val name: String? = fullName,
    val city: String? = null,
    val district: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarChatRequest(
    val message: String,
    @Json(name = "device_type") val device_type: String? = null,
    val brand: String? = null,
    val history: List<Map<String, String>>? = null
)

@JsonClass(generateAdapter = true)
data class KodyarChatResponse(
    val reply: String? = null,
    val status: String? = null,
    val suggestions: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class BazaarPaymentVerificationRequest(
    val sku: String? = null,
    @Json(name = "productId") val productId: String? = sku,
    @Json(name = "plan") val plan: String? = sku,
    val purchaseToken: String? = null,
    @Json(name = "purchase_token") val purchase_token: String? = purchaseToken,
    val packageName: String? = "com.example",
    @Json(name = "package_name") val package_name: String? = packageName,
    val price: Long? = null,
    @Json(name = "amount") val amount: Long? = price,
    val userId: String? = null,
    @Json(name = "user_id") val user_id: String? = userId,
    val phone: String? = null,
    val orderId: String? = null
)

@JsonClass(generateAdapter = true)
data class PartOrderPayload(
    val partId: String? = null,
    @Json(name = "part_id") val part_id: String? = partId,
    val quantity: Int = 1,
    val totalPrice: Double? = null,
    @Json(name = "total_price") val total_price: Double? = totalPrice,
    val address: String? = null,
    val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class UploadFileRequest(
    val name: String = "",
    val fileData: String = "" // data:image/jpeg;base64,...
)

@JsonClass(generateAdapter = true)
data class UploadFileResponse(
    val success: Boolean? = null,
    val url: String? = null,
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val error: String? = null
)


