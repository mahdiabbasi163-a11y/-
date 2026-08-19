package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AssistantRepository
import com.example.data.db.ConversationEntity
import com.example.data.db.MessageEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.db.CustomErrorEntity
import com.example.data.db.toDomain
import com.example.data.utils.NetworkMonitor
import com.example.data.model.*
import com.example.data.repository.ErrorCodeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun normalizePersian(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ة", "ه")
        .replace("\\s+".toRegex(), " ")
}

private fun normalizeDigitsAndTrim(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
        .replace('٠', '0')
        .replace('١', '1')
        .replace('٢', '2')
        .replace('٣', '3')
        .replace('٤', '4')
        .replace('٥', '5')
        .replace('٦', '6')
        .replace('٧', '7')
        .replace('٨', '8')
        .replace('٩', '9')
}

private fun normalizePhone(input: String?): String {
    var p = normalizeDigitsAndTrim(input).replace(" ", "").replace("-", "")
    if (p.startsWith("+98")) {
        p = "0" + p.substring(3)
    } else if (p.startsWith("0098")) {
        p = "0" + p.substring(4)
    } else if (p.startsWith("98") && p.length > 10) {
        p = "0" + p.substring(2)
    } else if (p.length == 10 && p.startsWith("9")) {
        p = "0$p"
    }
    return p
}

private fun hashPassword(pass: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pass.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModel() {

    private val networkMonitor = NetworkMonitor(context)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = networkMonitor.isCurrentlyOnline()
    )

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasLoadedFromNetwork = false

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("kodyar_prefs", Context.MODE_PRIVATE)

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "secure_kodyar_prefs",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to create EncryptedSharedPreferences, using fallback", e)
            context.getSharedPreferences("secure_kodyar_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val moshi = Moshi.Builder()
        .add(KodyarCityAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val databaseAdapter = moshi.adapter(KodyarDatabaseResponse::class.java)

    // --- Live Web Update Notification State ---
    private val _appUpdateNotification = MutableStateFlow<AppUpdateNotification?>(null)
    val appUpdateNotification: StateFlow<AppUpdateNotification?> = _appUpdateNotification.asStateFlow()

    fun dismissUpdateNotification() {
        _appUpdateNotification.value = null
    }

    // --- Version & Bazaar Update Dialog State ---
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow(AppUpdateInfo())
    val appUpdateInfo: StateFlow<AppUpdateInfo> = _appUpdateInfo.asStateFlow()

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun checkAppVersion(response: KodyarDatabaseResponse? = null) {
        val currentCode = com.example.BuildConfig.VERSION_CODE
        val currentVersionName = com.example.BuildConfig.VERSION_NAME
        val rawServerCode = response?.latestVersionCode ?: currentCode
        val serverVersionCode = rawServerCode.coerceAtLeast(currentCode)
        val serverVersionName = if (response?.latestVersionName != null && response.latestVersionName.isNotBlank()) response.latestVersionName else currentVersionName
        val notes = response?.updateNotes
        val isForce = response?.isForceUpdate ?: false

        _appUpdateInfo.value = _appUpdateInfo.value.copy(
            latestVersionCode = serverVersionCode,
            latestVersionName = serverVersionName,
            isForceUpdate = isForce,
            notes = if (!notes.isNullOrEmpty()) notes else _appUpdateInfo.value.notes
        )

        // If installed app version is less than server latest version code (e.g. user running version 32 or earlier)
        if (currentCode < serverVersionCode) {
            _showUpdateDialog.value = true
        }
    }

    // --- Theme Settings ---
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        sharedPrefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    // --- Disclaimer & Terms of Use State ---
    private val _isDisclaimerAccepted = MutableStateFlow(sharedPrefs.getBoolean("disclaimer_accepted", false))
    val isDisclaimerAccepted: StateFlow<Boolean> = _isDisclaimerAccepted.asStateFlow()

    fun acceptDisclaimer() {
        _isDisclaimerAccepted.value = true
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val user = _currentUser.value
        val userId = user?.id ?: "guest_${System.currentTimeMillis()}"
        val userPhone = user?.phone ?: "مهمان"
        val appVersion = com.example.BuildConfig.VERSION_NAME
        val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"

        sharedPrefs.edit()
            .putBoolean("disclaimer_accepted", true)
            .putString("disclaimer_accepted_at", dateStr)
            .putString("disclaimer_user_id", userId)
            .putString("disclaimer_user_phone", userPhone)
            .putString("disclaimer_app_version", appVersion)
            .putString("disclaimer_device_info", deviceInfo)
            .apply()
    }

    companion object {
        private val PROVINCE_FAMILIES = listOf(
            ProvinceFamily("اراک", listOf("اراک", "مرکزی", "استان مرکزی"), listOf("فرمهین", "ساوه", "خمین", "محلات", "شازند", "تفرش", "دلیجان", "زرندیه", "کمیجان", "آشتیان", "خنداب", "مامونیه", "غرق آباد", "میلاجرد", "ساروق", "نراق")),
            ProvinceFamily("تهران", listOf("تهران", "استان تهران"), listOf("ری", "شهر ری", "شمیرانات", "شمیران", "تجریش", "اسلامشهر", "شهریار", "دماوند", "ورامین", "پاکدشت", "رباط کریم", "قدس", "شهر قدس", "ملارد", "پردیس", "بهارستان", "قرچک", "فیروزکوه", "بومهن", "رودهن", "لواسان", "اندیشه", "صفادشت", "کهریزک", "حسن آباد")),
            ProvinceFamily("مشهد", listOf("مشهد", "خراسان", "خراسان رضوی"), listOf("نیشابور", "سبزوار", "تربت حیدریه", "قوچان", "چناران", "کاشمر", "تربت جام", "تایباد", "سرخس", "گناباد", "فریمان", "بینالود", "طرقبه", "شاندیز", "خواف", "بردسکن", "درگز", "کلات", "باخرز", "خلیل آباد")),
            ProvinceFamily("اصفهان", listOf("اصفهان", "استان اصفهان"), listOf("کاشان", "خمینی شهر", "نجف آباد", "شاهین شهر", "فولادشهر", "لنجان", "شهرضا", "مبارکه", "فلاورجان", "آران و بیدگل", "زرین شهر", "گلپایگان", "سمیرم", "خوانسار", "تیران", "داران", "نطنز", "اردستان", "نائین")),
            ProvinceFamily("شیراز", listOf("شیراز", "فارس", "استان فارس"), listOf("مرودشت", "کازرون", "جهرم", "لار", "لارستان", "فسا", "داراب", "فیروزآباد", "ممسنی", "نورآباد", "آباده", "اقلید", "سپیدان", "استهبان", "نی ریز", "لامرد", "کوار")),
            ProvinceFamily("تبریز", listOf("تبریز", "آذربایجان شرقی", "آذربایجان"), listOf("مراغه", "مرند", "میانه", "اهر", "بناب", "سراب", "آذرشهر", "اسکو", "شبستر", "عجب شیر", "ملکان", "هریس", "بستان آباد", "کلیبر", "جلفا", "سهند")),
            ProvinceFamily("اهواز", listOf("اهواز", "خوزستان", "استان خوزستان"), listOf("آبادان", "دزفول", "خرمشهر", "ماهشهر", "بندر ماهشهر", "ایذه", "بهبهان", "شوشتر", "شوش", "امیدیه", "مسجد سلیمان", "رامهرمز", "اندیمشک", "شادگان", "هندیجان", "سوسنگرد", "دشت آزادگان")),
            ProvinceFamily("کرج", listOf("کرج", "البرز", "استان البرز"), listOf("فردیس", "ساوجبلاغ", "نظرآباد", "هشتگرد", "طالقان", "اشتهارد", "کمال شهر", "محمدشهر", "ماهدشت", "گرمدره", "چهارباغ")),
            ProvinceFamily("قم", listOf("قم", "استان قم"), listOf("کهک", "جعفریه", "سلفچگان", "قنوات", "دستجرد")),
            ProvinceFamily("رشت", listOf("رشت", "گیلان", "استان گیلان"), listOf("انزلی", "بندر انزلی", "لاهیجان", "لنگرود", "فومن", "رودسر", "تالش", "هشتپر", "صومعه سرا", "آستارا", "آستانه اشرفیه", "رودبار", "منجیل", "لوشان", "ماسوله", "ماسال", "شفت", "سیاهکل", "رضوانشهر")),
            ProvinceFamily("ساری", listOf("ساری", "مازندران", "استان مازندران"), listOf("بابل", "آمل", "قائم شهر", "قائمشهر", "تنکابن", "شهسوار", "چالوس", "نوشهر", "بابلسر", "رامسر", "محمودآباد", "نور", "نکا", "بهشهر", "فریدونکنار", "جویبار", "سوادکوه", "زیرآب", "پل سفید", "کلاردشت", "عباس آباد", "رویان")),
            ProvinceFamily("کرمانشاه", listOf("کرمانشاه", "استان کرمانشاه"), listOf("اسلام آباد غرب", "کنگاور", "سنقر", "جوانرود", "صحنه", "هرسین", "سرپل ذهاب", "پاوه", "روانسر", "گیلانغرب", "قصر شیرین", "تازه آباد")),
            ProvinceFamily("ارومیه", listOf("ارومیه", "آذربایجان غربی"), listOf("خوی", "بوکان", "مهاباد", "میاندوآب", "سلماس", "پیرانشهر", "نقده", "تکاب", "ماکو", "سردشت", "شاهین دژ", "اشنویه", "قره ضیاءالدین", "سیه چشمه")),
            ProvinceFamily("یزد", listOf("یزد", "استان یزد"), listOf("میبد", "اردکان", "مهریز", "بافق", "ابرکوه", "تفت", "اشکذر", "هرات", "مروست", "بهاباد")),
            ProvinceFamily("کرمان", listOf("کرمان", "استان کرمان"), listOf("رفسنجان", "سیرجان", "جیرفت", "بم", "زرند", "کهنوج", "شهر بابک", "بافت", "بردسیر", "عنبرآباد", "منوجان", "راور")),
            ProvinceFamily("همدان", listOf("همدان", "استان همدان"), listOf("ملایر", "نهاوند", "تویسرکان", "کبودرآهنگ", "بهار", "رزن", "فامنین", "لالجین", "مریانج", "قروه درجزین")),
            ProvinceFamily("خرم آباد", listOf("خرم آباد", "لرستان", "استان لرستان"), listOf("بروجرد", "دورود", "الیگودرز", "کوهدشت", "ازنا", "پلدختر", "الشتر", "سلسله", "نورآباد", "دلفان", "چگنی")),
            ProvinceFamily("قزوین", listOf("قزوین", "استان قزوین"), listOf("الوند", "البرز قزوین", "تاکستان", "بوئین زهرا", "آبیک", "محمدیه", "محمودآباد نمونه", "اقبالیه", "شریفیه", "ضیاءآباد")),
            ProvinceFamily("زنجان", listOf("زنجان", "استان زنجان"), listOf("ابهر", "خرمدره", "قیدار", "خدابنده", "طارم", "آب بر", "ماهنشان", "ایجرود", "زرین آباد", "سلطانیه")),
            ProvinceFamily("سمنان", listOf("سمنان", "استان سمنان"), listOf("شاهرود", "دامغان", "گرمسار", "مهدی شهر", "سنگسر", "سرخه", "آرادان", "میامی", "بسطام", "شهمیرزاد")),
            ProvinceFamily("گرگان", listOf("گرگان", "گلستان", "استان گلستان"), listOf("گنبد کاووس", "گنبد", "علی آباد کتول", "بندر ترکمن", "آق قلا", "کلاله", "آزادشهر", "کردکوی", "مینودشت", "گالیکش", "بندر گز", "رامیان", "مراوه تپه", "گمیشان")),
            ProvinceFamily("بوشهر", listOf("بوشهر", "استان بوشهر"), listOf("برازجان", "دشتستان", "گناوه", "بندر گناوه", "کنگان", "بندر کنگان", "عسلویه", "خورموج", "دشتی", "جم", "دیلم", "بندر دیلم", "اهرم", "تنگستان", "دیر", "بندر دیر")),
            ProvinceFamily("بندر عباس", listOf("بندر عباس", "بندرعباس", "هرمزگان", "استان هرمزگان"), listOf("قشم", "کیش", "میناب", "بندرلنگه", "لنگه", "رودان", "بستک", "حاجی آباد", "جاسک", "بندر خمیر", "پارسیان", "گاوبندی", "سیریک", "بشاگرد")),
            ProvinceFamily("زاهدان", listOf("زاهدان", "سیستان و بلوچستان", "سیستان", "بلوچستان"), listOf("زابل", "ایرانشهر", "چابهار", "بندر چابهار", "سراوان", "خاش", "نیک شهر", "کنارک", "راسک", "سرباز", "میرجاوه", "زهک", "هیرمند", "قصرقند")),
            ProvinceFamily("سنندج", listOf("سنندج", "کردستان", "استان کردستان"), listOf("سقز", "مریوان", "بانه", "قروه", "کامیاران", "بیجار", "دیواندره", "دهگلان", "سروآباد")),
            ProvinceFamily("اردبیل", listOf("اردبیل", "استان اردبیل"), listOf("پارس آباد", "مشگین شهر", "خلخال", "گرمی", "نمین", "بیله سوار", "کوثر", "گیوی", "سرعین", "نیر", "اصلاندوز")),
            ProvinceFamily("شهرکرد", listOf("شهرکرد", "چهارمحال و بختیاری", "چهارمحال"), listOf("بروجن", "فارسان", "لردگان", "فرخ شهر", "سامان", "بن", "کیار", "شلمزار", "کوهرنگ", "چلگرد", "اردل", "خانمیرزا")),
            ProvinceFamily("ایلام", listOf("ایلام", "استان ایلام"), listOf("دهلران", "ایوان", "آبدانان", "مهران", "دره شهر", "چرداول", "سرابله", "بدره", "ملکشاهی", "سیروان")),
            ProvinceFamily("یاسوج", listOf("یاسوج", "کهگیلویه و بویراحمد", "کهگیلویه"), listOf("دوگنبدان", "گچساران", "دهدشت", "لیکک", "بهمئی", "چرام", "لنده", "سی سخت", "دنا", "باشت", "مارگون")),
            ProvinceFamily("بجنورد", listOf("بجنورد", "خراسان شمالی"), listOf("شیروان", "اسفراین", "گرمه", "جاجرم", "آشخانه", "مانه و سملقان", "فاروج", "راز و جرگلان")),
            ProvinceFamily("بیرجند", listOf("بیرجند", "خراسان جنوبی"), listOf("قائنات", "قائن", "فردوس", "طبس", "نهبندان", "سرایان", "سربیشه", "بشرویه", "درمیان", "اسدیه", "خوسف", "زیرکوه"))
        )
    }

    private data class ProvinceFamily(
        val family: String,
        val aliases: List<String>,
        val regions: List<String>
    )

    fun areCitiesCompatible(cityA: String?, cityB: String?): Boolean {
        fun cleanCityNorm(input: String?): String {
            var s = normalizePersian(input)
            val prefixes = listOf("شهرستان ", "شهر ", "استان ", "بخش ", "حومه ", "منطقه ", "روستای ", "روستا ", "شهرک ")
            for (p in prefixes) {
                if (s.startsWith(p)) {
                    s = s.removePrefix(p).trim()
                }
            }
            return s
        }

        val normA = normalizePersian(cityA)
        val normB = normalizePersian(cityB)
        if (normA.isEmpty() || normB.isEmpty()) return true
        if (normA == "همه" || normB == "همه" || normA == "all" || normB == "all") return true
        
        val cleanA = cleanCityNorm(cityA)
        val cleanB = cleanCityNorm(cityB)
        
        // Direct match or substring match on raw or cleaned names
        if (normA == normB || cleanA == cleanB ||
            normA.contains(normB, ignoreCase = true) || normB.contains(normA, ignoreCase = true) ||
            cleanA.contains(cleanB, ignoreCase = true) || cleanB.contains(cleanA, ignoreCase = true)) {
            return true
        }
        
        // 1. Dynamic match using server-provided center/regions structure
        val centers = _liveCitiesStructured.value
        if (centers.isNotEmpty()) {
            fun centerNameFor(cityNorm: String, cityClean: String): String? {
                for (center in centers) {
                    val centerName = normalizePersian(
                        center.name ?: center.title ?: center.city ?: center.cityName ?: center.name_fa ?: center.nameFarsi
                    )
                    val centerClean = cleanCityNorm(centerName)
                    if (centerName.isEmpty()) continue
                    if (centerName == cityNorm || centerClean == cityClean || cityNorm.contains(centerName) || centerName.contains(cityNorm)) return centerName
                    val regionMatch = center.regions?.any { 
                        val rNorm = normalizePersian(it)
                        val rClean = cleanCityNorm(rNorm)
                        rNorm == cityNorm || rClean == cityClean || cityNorm.contains(rNorm) || rNorm.contains(cityNorm)
                    } == true
                    if (regionMatch) return centerName
                }
                return null
            }
            val centerA = centerNameFor(normA, cleanA)
            val centerB = centerNameFor(normB, cleanB)
            if (centerA != null && centerB != null && centerA == centerB) {
                return true
            }
        }
        
        // 2. Built-in full province families dictionary (synced with server)
        fun findFamilies(textNorm: String, textClean: String): Set<String> {
            val result = mutableSetOf<String>()
            for (pf in PROVINCE_FAMILIES) {
                val fNorm = normalizePersian(pf.family)
                val fClean = cleanCityNorm(fNorm)
                val isFamilyMatch = textNorm.contains(fNorm) || fNorm.contains(textNorm) ||
                                   textClean.contains(fClean) || fClean.contains(textClean)
                val isAliasMatch = pf.aliases.any { 
                    val aNorm = normalizePersian(it)
                    val aClean = cleanCityNorm(aNorm)
                    textNorm.contains(aNorm) || aNorm.contains(textNorm) ||
                    textClean.contains(aClean) || aClean.contains(textClean)
                }
                val isRegionMatch = pf.regions.any { 
                    val rNorm = normalizePersian(it)
                    val rClean = cleanCityNorm(rNorm)
                    textNorm.contains(rNorm) || rNorm.contains(textNorm) ||
                    textClean.contains(rClean) || rClean.contains(textClean)
                }
                if (isFamilyMatch || isAliasMatch || isRegionMatch) {
                    result.add(fNorm)
                }
            }
            return result
        }

        val famA = findFamilies(normA, cleanA)
        val famB = findFamilies(normB, cleanB)
        if (famA.isNotEmpty() && famB.isNotEmpty()) {
            if (famA.any { famB.contains(it) }) {
                return true
            }
        }
        
        return false
    }

    // --- Database states ---
    private val _liveErrorCodes = MutableStateFlow<List<KodyarErrorCode>>(emptyList())
    val liveErrorCodes: StateFlow<List<KodyarErrorCode>> = _liveErrorCodes.asStateFlow()

    private val _liveSpareParts = MutableStateFlow<List<KodyarSparePart>>(emptyList())
    val liveSpareParts: StateFlow<List<KodyarSparePart>> = _liveSpareParts.asStateFlow()

    private val _storeSearchQuery = MutableStateFlow("")
    val storeSearchQuery: StateFlow<String> = _storeSearchQuery.asStateFlow()

    fun setStoreSearchQuery(query: String) {
        _storeSearchQuery.value = query
    }

    fun clearStoreSearchQuery() {
        _storeSearchQuery.value = ""
    }

    private val _liveTechnicians = MutableStateFlow<List<KodyarTechnician>>(emptyList())
    val liveTechnicians: StateFlow<List<KodyarTechnician>> = _liveTechnicians.asStateFlow()

    private val _liveCommonProblems = MutableStateFlow<List<KodyarCommonProblem>>(emptyList())
    val liveCommonProblems: StateFlow<List<KodyarCommonProblem>> = _liveCommonProblems.asStateFlow()

    private val _liveCategories = MutableStateFlow<List<String>>(listOf("همه"))
    val liveCategories: StateFlow<List<String>> = _liveCategories.asStateFlow()

    private val _liveBrands = MutableStateFlow<List<String>>(listOf("همه"))
    val liveBrands: StateFlow<List<String>> = _liveBrands.asStateFlow()

    private val _liveCities = MutableStateFlow<List<String>>(listOf("همه"))
    val liveCities: StateFlow<List<String>> = _liveCities.asStateFlow()

    private val _liveCitiesStructured = MutableStateFlow<List<KodyarCity>>(emptyList())
    val liveCitiesStructured: StateFlow<List<KodyarCity>> = _liveCitiesStructured.asStateFlow()

    private val _isDatabaseLoading = MutableStateFlow(false)
    val isDatabaseLoading: StateFlow<Boolean> = _isDatabaseLoading.asStateFlow()

    // --- Referral / Invite Code States ---
    private val _appliedReferralCode = MutableStateFlow<String?>(null)
    val appliedReferralCode: StateFlow<String?> = _appliedReferralCode.asStateFlow()

    private val _referralDiscountPercent = MutableStateFlow(0)
    val referralDiscountPercent: StateFlow<Int> = _referralDiscountPercent.asStateFlow()

    fun applyReferralCode(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length < 4) return false
        _appliedReferralCode.value = trimmed
        _referralDiscountPercent.value = 25 // Default 25%
        return true
    }

    fun removeReferralCode() {
        _appliedReferralCode.value = null
        _referralDiscountPercent.value = 0
    }

    // --- Search states ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedBrand = MutableStateFlow("همه")
    val selectedBrand: StateFlow<String> = _selectedBrand.asStateFlow()

    private val _selectedCategory = MutableStateFlow("همه")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _modelQuery = MutableStateFlow("")
    val modelQuery: StateFlow<String> = _modelQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<KodyarErrorCode>>(emptyList())
    val searchResults: StateFlow<List<KodyarErrorCode>> = _searchResults.asStateFlow()

    private val _showOnlySaved = MutableStateFlow(false)
    val showOnlySaved: StateFlow<Boolean> = _showOnlySaved.asStateFlow()

    // --- Bookmarked / Saved Errors ---
    val savedErrors: StateFlow<List<SavedErrorEntity>> = repository.allSavedErrors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setShowOnlySaved(onlySaved: Boolean) {
        _showOnlySaved.value = onlySaved
        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
    }

    // --- Auth states ---
    private val _currentUser = MutableStateFlow<KodyarUser?>(null)
    val currentUser: StateFlow<KodyarUser?> = _currentUser
        .map { user ->
            if (user != null && sharedPrefs.getBoolean("bazaar_premium_active", false)) {
                val sku = sharedPrefs.getString("bazaar_premium_sku", "") ?: ""
                user.copy(
                    subscription = KodyarSubscription(
                        is_premium = true,
                        expiry_date = calculateExpiryDateForSku(sku)
                    )
                )
            } else {
                user
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // --- Subscription Plans state ---
    private val _subscriptionPlans = MutableStateFlow<List<KodyarSubscriptionPlan>>(emptyList())
    val subscriptionPlans: StateFlow<List<KodyarSubscriptionPlan>> = _subscriptionPlans.asStateFlow()

    private val _isPlansLoading = MutableStateFlow(false)
    val isPlansLoading: StateFlow<Boolean> = _isPlansLoading.asStateFlow()

    // --- Cart states ---
    private val _cart = MutableStateFlow<List<String>>(emptyList())
    val cart: StateFlow<List<String>> = _cart.asStateFlow()

    private val _cartQty = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cartQty: StateFlow<Map<String, Int>> = _cartQty.asStateFlow()

    private val _isPurchaseLoading = MutableStateFlow(false)
    val isPurchaseLoading: StateFlow<Boolean> = _isPurchaseLoading.asStateFlow()

    private val _purchaseSuccess = MutableStateFlow(false)
    val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    // --- Repair state ---
    private val _repairOrders = MutableStateFlow<List<KodyarRepairOrder>>(emptyList())
    val repairOrders: StateFlow<List<KodyarRepairOrder>> = _repairOrders.asStateFlow()

    private val _isRepairsLoading = MutableStateFlow(false)
    val isRepairsLoading: StateFlow<Boolean> = _isRepairsLoading.asStateFlow()

    // --- Part Purchase state ---
    private val _partPurchases = MutableStateFlow<List<PartPurchaseOrder>>(emptyList())
    val partPurchases: StateFlow<List<PartPurchaseOrder>> = _partPurchases.asStateFlow()

    private val partPurchasesAdapter by lazy {
        moshi.adapter<List<PartPurchaseOrder>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, PartPurchaseOrder::class.java)
        )
    }

    fun loadPartPurchases() {
        val json = sharedPrefs.getString("part_purchases_json", null)
        if (!json.isNullOrEmpty()) {
            try {
                val list = partPurchasesAdapter.fromJson(json)
                if (list != null) {
                    _partPurchases.value = list
                }
            } catch (e: java.lang.Exception) {
                Log.e("AssistantViewModel", "Error parsing part purchases", e)
            }
        }
    }

    fun savePartPurchase(order: PartPurchaseOrder) {
        val currentList = _partPurchases.value.toMutableList()
        currentList.add(0, order) // Add at top
        _partPurchases.value = currentList
        try {
            val json = partPurchasesAdapter.toJson(currentList)
            sharedPrefs.edit().putString("part_purchases_json", json).apply()
        } catch (e: java.lang.Exception) {
            Log.e("AssistantViewModel", "Error saving part purchases", e)
        }
    }

    private fun getCurrentPersianDate(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val pYear = year - 621
        return "$pYear/$month/$day"
    }

    private fun calculateExpiryDateForSku(sku: String): String {
        val calendar = java.util.Calendar.getInstance()
        val daysToAdd = when (sku) {
            "ir.golden.com" -> 30
            "ir.silver.com" -> 90
            "ir.almas.com" -> 180
            "ir.12-month.com" -> 365
            else -> 30
        }
        calendar.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd)
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "$year-${String.format("%02d", month)}-${String.format("%02d", day)}"
    }

    // --- Sub / Verification state ---
    private val _isCardVerifyLoading = MutableStateFlow(false)
    val isCardVerifyLoading: StateFlow<Boolean> = _isCardVerifyLoading.asStateFlow()

    private val _cardVerifySuccess = MutableStateFlow(false)
    val cardVerifySuccess: StateFlow<Boolean> = _cardVerifySuccess.asStateFlow()

    // --- Usage counts ---
    private val _freeErrorCount = MutableStateFlow(0)
    val freeErrorCount: StateFlow<Int> = _freeErrorCount.asStateFlow()

    private val _freeProblemCount = MutableStateFlow(0)
    val freeProblemCount: StateFlow<Int> = _freeProblemCount.asStateFlow()

    init {
        loadPersistedCart()
        observeRoomDatabase()
        viewModelScope.launch(Dispatchers.IO) {
            if (!sharedPrefs.getBoolean("v3_test_kodyar_cache_purged", false)) {
                try {
                    repository.clearAllOfflineCache()
                    sharedPrefs.edit()
                        .remove("cached_kodyar_database")
                        .putBoolean("v3_test_kodyar_cache_purged", true)
                        .apply()
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Failed to purge old domain cache", e)
                }
            } else {
                loadCachedDatabase()
            }
            checkSavedSession()
            loadKodyarDatabase()
            refreshTechnicians()
            fetchSubscriptionPlans()
            loadPartPurchases()
        }
        setupNetworkCallback()
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    Log.d("AssistantViewModel", "Network state flow detected online. Fetching fresh Kodyar server data...")
                    loadKodyarDatabase()
                    fetchSubscriptionPlans()
                    val token = getSessionToken()
                    if (token != null) {
                        loadCurrentUser(token)
                    }
                }
            }
        }
        viewModelScope.launch {
            savedErrors.collect {
                if (_showOnlySaved.value) {
                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                }
            }
        }
    }

    private fun observeRoomDatabase() {
        repository.getCachedErrorCodes()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    val domainList = entities.map { it.toDomain() }
                    _liveErrorCodes.value = domainList

                    val categories = listOf("همه") + domainList.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct()
                    val brands = listOf("همه") + domainList.mapNotNull { it.brand }.filter { it.isNotBlank() }.distinct()
                    if (categories.size > 1) _liveCategories.value = categories
                    if (brands.size > 1) _liveBrands.value = brands
                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                }
            }
        }

        repository.getCachedSpareParts()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    _liveSpareParts.value = entities.map { it.toDomain() }
                }
            }
        }

        repository.getCachedCommonProblems()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    _liveCommonProblems.value = entities.map { it.toDomain() }
                }
            }
        }

        repository.getCachedTechnicians()?.let { flow ->
            viewModelScope.launch(Dispatchers.IO) {
                flow.collect { entities ->
                    _liveTechnicians.value = entities.map { it.toDomain() }
                }
            }
        }
    }

    fun fetchSubscriptionPlans() {
        viewModelScope.launch {
            _isPlansLoading.value = true
            try {
                val response = repository.getSubscriptionPlans()
                if ((response.status == "ok" || response.status == "success") && response.plans != null) {
                    _subscriptionPlans.value = response.plans
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to fetch subscription plans: ${e.message}")
            } finally {
                _isPlansLoading.value = false
            }
        }
    }

    private fun loadCachedDatabase() {
        try {
            val cachedJson = sharedPrefs.getString("cached_kodyar_database", null)
            if (!cachedJson.isNullOrEmpty()) {
                val response = databaseAdapter.fromJson(cachedJson)
                if (response != null) {
                    _liveErrorCodes.value = response.resolvedErrorCodes
                    _liveSpareParts.value = response.resolvedSpareParts
                    _liveTechnicians.value = response.resolvedTechnicians
                    _liveCommonProblems.value = response.resolvedCommonProblems

                    val dynamicCats = (response.resolvedCategoriesList + response.resolvedErrorCodes.mapNotNull { it.resolvedCategory }).filter { it.isNotBlank() }.distinct()
                    _liveCategories.value = listOf("همه") + dynamicCats

                    val dynamicBrands = (response.resolvedBrandsList + response.resolvedErrorCodes.mapNotNull { it.brand }).filter { it.isNotBlank() }.distinct()
                    _liveBrands.value = listOf("همه") + dynamicBrands
                    val parsedCities = response.resolvedCitiesList.flatMap { 
                        listOfNotNull(it.name, it.title, it.city, it.cityName, it.name_fa, it.nameFarsi, it.slug)
                    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    _liveCities.value = listOf("همه") + parsedCities
                    _liveCitiesStructured.value = response.resolvedCitiesList

                    updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
                    Log.d("AssistantViewModel", "Successfully loaded database from local cache.")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to load cached database.", e)
        }
        // If loading cache failed or is empty, initialize default empty state
        ensureDefaultFilters()
    }

    private fun ensureDefaultFilters() {
        if (_liveCategories.value.isEmpty()) {
            _liveCategories.value = listOf("همه")
        }
        if (_liveBrands.value.isEmpty()) {
            _liveBrands.value = listOf("همه")
        }
        if (_liveCities.value.isEmpty()) {
            _liveCities.value = listOf("همه")
        }
        updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)
    }

    private fun saveSessionToken(token: String) {
        try {
            encryptedPrefs.edit().putString("session_token", token).apply()
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to save encrypted token, saving to sharedPrefs", e)
            sharedPrefs.edit().putString("session_token", token).apply()
        }
    }

    private fun clearSessionToken() {
        try {
            encryptedPrefs.edit().remove("session_token").apply()
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to clear encrypted token", e)
        }
        sharedPrefs.edit().remove("session_token").apply()
    }

    // --- Session & Auth functions ---
    fun getSessionToken(): String? {
        val token = try {
            encryptedPrefs.getString("session_token", null)
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to read encrypted token", e)
            null
        }
        if (!token.isNullOrBlank()) {
            return token
        }
        val legacyToken = sharedPrefs.getString("session_token", null)
        if (!legacyToken.isNullOrBlank()) {
            saveSessionToken(legacyToken)
            sharedPrefs.edit().remove("session_token").apply()
            return legacyToken
        }
        return null
    }

    private fun checkSavedSession() {
        val token = getSessionToken()
        if (token != null) {
            val cached = getCachedUser()
            if (cached != null) {
                _currentUser.value = cached
            }
            loadCurrentUser(token)
        }
    }

    private fun saveUserToCache(user: KodyarUser) {
        val cached = getCachedUser()
        val persistentCity = if (!user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${user.phone}", null) else null
        val userCity = user.resolvedCity
        val finalCity = if (userCity.isNotBlank()) userCity else (cached?.resolvedCity ?: persistentCity)
        val finalRole = if (!user.role.isNullOrBlank()) user.role else (cached?.role ?: "customer")
        val finalCategories = if (!user.categories.isNullOrEmpty()) user.categories else cached?.categories
        val finalIsApproved = user.isApprovedUser || (cached?.isApprovedUser ?: false)
        val finalApprovalStatus = if (finalIsApproved) "approved" else (user.approval_status ?: (cached?.approval_status ?: "pending"))

        val editor = sharedPrefs.edit()
            .putString("cached_user_id", user.id)
            .putString("cached_user_name", user.full_name)
            .putString("cached_user_phone", user.phone)
            .putBoolean("cached_user_premium", user.subscription?.is_premium ?: false)
            .putString("cached_user_expiry", user.subscription?.expiry_date)
            .putString("cached_user_role", finalRole)
            .putString("cached_user_city", finalCity)
            .putString("cached_user_categories", finalCategories?.joinToString(","))
            .putBoolean("cached_user_is_approved", finalIsApproved)
            .putString("cached_user_approval_status", finalApprovalStatus)

        if (!finalCity.isNullOrBlank() && !user.phone.isNullOrBlank()) {
            editor.putString("persistent_city_${user.phone}", finalCity)
        }
        editor.apply()
    }

    fun updateUserCityLocally(user: KodyarUser) {
        _currentUser.value = user
        saveUserToCache(user)
    }

    fun setPremiumUserLocally(sku: String) {
        val isPremium = sku.isNotEmpty()
        sharedPrefs.edit()
            .putBoolean("bazaar_premium_active", isPremium)
            .putString("bazaar_premium_sku", sku)
            .apply()

        val user = _currentUser.value
        if (user != null) {
            val updatedUser = user.copy(
                subscription = KodyarSubscription(
                    is_premium = isPremium,
                    expiry_date = if (isPremium) calculateExpiryDateForSku(sku) else ""
                )
            )
            _currentUser.value = updatedUser
            saveUserToCache(updatedUser)
        } else {
            // Trigger flow update
            _currentUser.value = null
        }
    }

    fun syncBazaarPurchaseToServer(sku: String, purchaseToken: String, orderId: String? = null) {
        setPremiumUserLocally(sku)
        val token = getSessionToken()
        val user = _currentUser.value
        val appPkg = context.packageName ?: "com.example"

        viewModelScope.launch {
            try {
                Log.d("AssistantViewModel", "Sending Bazaar purchase verification to server: sku=$sku")
                val response = repository.verifyBazaarPayment(
                    token = token,
                    sku = sku,
                    purchaseToken = purchaseToken,
                    packageName = appPkg,
                    userId = user?.id,
                    phone = user?.phone,
                    orderId = orderId
                )

                if (response.status == "ok" || response.status == "success" || response.success == true) {
                    Log.d("AssistantViewModel", "Bazaar purchase verified and recorded in server database successfully.")
                    if (token != null) {
                        loadCurrentUser(token)
                    }
                } else {
                    Log.w("AssistantViewModel", "Server returned non-ok for Bazaar purchase verification: ${response.error ?: response.message}")
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to sync Bazaar purchase to server database", e)
            }
        }
    }

    private fun getCachedUser(): KodyarUser? {
        val id = sharedPrefs.getString("cached_user_id", null) ?: return null
        val name = sharedPrefs.getString("cached_user_name", "") ?: ""
        val phone = sharedPrefs.getString("cached_user_phone", "") ?: ""
        val isPremium = sharedPrefs.getBoolean("cached_user_premium", false)
        val expiry = sharedPrefs.getString("cached_user_expiry", null)
        val role = sharedPrefs.getString("cached_user_role", "customer")
        val city = sharedPrefs.getString("cached_user_city", null)
        val catsString = sharedPrefs.getString("cached_user_categories", null)
        val categories = if (!catsString.isNullOrEmpty()) catsString.split(",") else null
        val isApproved = sharedPrefs.getBoolean("cached_user_is_approved", true)
        val approvalStatus = sharedPrefs.getString("cached_user_approval_status", "approved") ?: "approved"
        return KodyarUser(
            id = id,
            full_name = name,
            phone = phone,
            subscription = KodyarSubscription(is_premium = isPremium, expiry_date = expiry),
            role = role,
            city = city,
            categories = categories,
            is_approved = isApproved,
            approval_status = approvalStatus,
            is_verified = isApproved,
            isVerified = isApproved,
            status = if (isApproved) "verified" else "pending"
        )
    }

    private fun clearUserCache() {
        sharedPrefs.edit()
            .remove("cached_user_id")
            .remove("cached_user_name")
            .remove("cached_user_phone")
            .remove("cached_user_premium")
            .remove("cached_user_expiry")
            .remove("cached_user_role")
            .remove("cached_user_city")
            .remove("cached_user_categories")
            .remove("cached_user_is_approved")
            .remove("cached_user_approval_status")
            .apply()
    }

    fun getUniqueErrorCodesViewed(): Set<String> {
        return sharedPrefs.getStringSet("viewed_error_codes", emptySet()) ?: emptySet()
    }

    fun recordErrorCodeView(codeKey: String) {
        val current = getUniqueErrorCodesViewed().toMutableSet()
        current.add(codeKey)
        sharedPrefs.edit().putStringSet("viewed_error_codes", current).apply()
    }

    fun getUniqueProblemsViewed(): Set<String> {
        return sharedPrefs.getStringSet("viewed_problems", emptySet()) ?: emptySet()
    }

    fun recordProblemView(problemKey: String) {
        val current = getUniqueProblemsViewed().toMutableSet()
        current.add(problemKey)
        sharedPrefs.edit().putStringSet("viewed_problems", current).apply()
    }

    private fun extractSubscription(user: KodyarUser, responseSub: KodyarSubscription?): KodyarSubscription {
        if (user.subscription != null && (user.subscription.is_premium || !user.subscription.expiry_date.isNullOrBlank())) {
            return user.subscription
        }
        if (responseSub != null && (responseSub.is_premium || !responseSub.expiry_date.isNullOrBlank())) {
            return responseSub
        }
        val isPrem = user.is_premium == true
        val exp = user.expiry_date ?: user.subscription_expiry
        if (isPrem || !exp.isNullOrBlank()) {
            return KodyarSubscription(is_premium = isPrem || !exp.isNullOrBlank(), expiry_date = exp)
        }
        return user.subscription ?: KodyarSubscription(is_premium = false, expiry_date = null)
    }

    fun loadCurrentUser(token: String) {
        viewModelScope.launch {
            try {
                val response = repository.getMe(token)
                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val cached = getCachedUser()
                    val persistentCity = if (!response.user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${response.user.phone}", null) else null
                    val sub = extractSubscription(response.user, response.subscription)
                    val isApprovedFromTechs = _liveTechnicians.value.any { 
                        (it.id == response.user.id || (!it.name.isNullOrBlank() && it.name == response.user.full_name)) && it.isVerified == true 
                    }
                    val isApproved = response.user.isApprovedUser || isApprovedFromTechs
                    val mergedUser = response.user.copy(
                        subscription = sub,
                        city = if (!response.user.resolvedCity.isNullOrBlank()) response.user.resolvedCity else (cached?.resolvedCity ?: persistentCity),
                        role = if (!response.user.role.isNullOrBlank()) response.user.role else (cached?.role ?: "customer"),
                        categories = if (!response.user.categories.isNullOrEmpty()) response.user.categories else cached?.categories,
                        is_approved = isApproved,
                        approval_status = if (isApproved) "approved" else "pending",
                        is_verified = isApproved,
                        isVerified = isApproved,
                        status = if (isApproved) "verified" else (response.user.status ?: "pending")
                    )
                    _currentUser.value = mergedUser
                    saveUserToCache(mergedUser)
                    loadFreeStatus()
                    loadRepairs()
                } else if (response.status == "error") {
                    val cached = getCachedUser()
                    if (cached != null) {
                        _currentUser.value = cached
                    } else {
                        logout()
                    }
                } else {
                    logout()
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error fetching user", e)
                val cached = getCachedUser()
                if (cached != null) {
                    _currentUser.value = cached
                }
            }
        }
    }

    fun login(phone: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        val cleanPass = normalizeDigitsAndTrim(pass)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                var response = repository.login(cleanPhone, cleanPass)
                if (response.status != "ok" && response.status != "success") {
                    val altPhone = if (cleanPhone.startsWith("0")) cleanPhone.removePrefix("0") else "0$cleanPhone"
                    val response2 = repository.login(altPhone, cleanPass)
                    if (response2.status == "ok" || response2.status == "success") {
                        response = response2
                    }
                }

                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val cached = getCachedUser()
                    val persistentCity = if (!response.user.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${response.user.phone}", null) else null
                    val sub = extractSubscription(response.user, response.subscription)
                    val matchedTech = _liveTechnicians.value.find { tech ->
                        tech.id == response.user.id || 
                        (!tech.name.isNullOrBlank() && tech.name == response.user.full_name)
                    }
                    val isApprovedFromTechs = matchedTech?.isVerified == true
                    val isApproved = response.user.isApprovedUser || isApprovedFromTechs
                    val isTech = response.user.role == "technician" || matchedTech != null
                    val finalRole = if (isTech) "technician" else if (!response.user.role.isNullOrBlank()) response.user.role else (cached?.role ?: "customer")
                    val userCats = if (!response.user.categories.isNullOrEmpty()) response.user.categories else response.user.specialty
                    val finalCategories = if (!userCats.isNullOrEmpty()) userCats else (matchedTech?.resolvedCategories ?: cached?.categories)

                    val mergedUser = response.user.copy(
                        subscription = sub,
                        phone = if (!response.user.phone.isNullOrBlank()) response.user.phone else cleanPhone,
                        city = if (!response.user.resolvedCity.isNullOrBlank()) response.user.resolvedCity else (cached?.resolvedCity ?: persistentCity),
                        role = finalRole,
                        categories = finalCategories,
                        is_approved = isApproved,
                        approval_status = if (isApproved) "approved" else "pending",
                        is_verified = isApproved,
                        isVerified = isApproved,
                        status = if (isApproved) "verified" else (response.user.status ?: "pending")
                    )
                    val tokenToSave = response.token ?: response.session_token ?: response.user.id
                    _currentUser.value = mergedUser
                    saveUserToCache(mergedUser)
                    saveSessionToken(tokenToSave)
                    sharedPrefs.edit()
                        .remove("session_token")
                        .putString("local_user_pass_${cleanPhone}", hashPassword(cleanPass))
                        .putString("local_user_pass_${cleanPhone.removePrefix("0")}", hashPassword(cleanPass))
                        .apply()
                    loadFreeStatus()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    // Check local credentials fallback
                    val savedPass = sharedPrefs.getString("local_user_pass_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_pass_${cleanPhone.removePrefix("0")}", null)
                    if (savedPass != null && savedPass == hashPassword(cleanPass)) {
                        val savedName = sharedPrefs.getString("local_user_name_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_name_${cleanPhone.removePrefix("0")}", "کاربر کدیار")
                        val savedRole = sharedPrefs.getString("local_user_role_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_role_${cleanPhone.removePrefix("0")}", "customer")
                        val savedCity = sharedPrefs.getString("local_user_city_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_city_${cleanPhone.removePrefix("0")}", "تهران")
                        val savedCatsStr = sharedPrefs.getString("local_user_cats_${cleanPhone}", null)
                            ?: sharedPrefs.getString("local_user_cats_${cleanPhone.removePrefix("0")}", null)
                        val savedCats = if (!savedCatsStr.isNullOrEmpty()) savedCatsStr.split(",") else null

                        val localUser = KodyarUser(
                            id = "user_${cleanPhone}",
                            full_name = savedName ?: "کاربر کدیار",
                            phone = cleanPhone,
                            role = savedRole ?: "customer",
                            city = savedCity,
                            categories = savedCats,
                            is_approved = true,
                            approval_status = "approved",
                            is_verified = true,
                            isVerified = true
                        )
                        _currentUser.value = localUser
                        saveUserToCache(localUser)
                        saveSessionToken(localUser.id)
                        sharedPrefs.edit().remove("session_token").apply()
                        loadFreeStatus()
                        loadRepairs()
                        onResult(true, null)
                    } else {
                        _authError.value = response.error ?: "نام کاربری یا رمز عبور اشتباه است"
                        onResult(false, _authError.value)
                    }
                }
            } catch (e: Exception) {
                // Check local credentials fallback on network error
                val savedPass = sharedPrefs.getString("local_user_pass_${cleanPhone}", null)
                    ?: sharedPrefs.getString("local_user_pass_${cleanPhone.removePrefix("0")}", null)
                if (savedPass != null && savedPass == hashPassword(cleanPass)) {
                    val savedName = sharedPrefs.getString("local_user_name_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_name_${cleanPhone.removePrefix("0")}", "کاربر کدیار")
                    val savedRole = sharedPrefs.getString("local_user_role_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_role_${cleanPhone.removePrefix("0")}", "customer")
                    val savedCity = sharedPrefs.getString("local_user_city_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_city_${cleanPhone.removePrefix("0")}", "تهران")
                    val savedCatsStr = sharedPrefs.getString("local_user_cats_${cleanPhone}", null)
                        ?: sharedPrefs.getString("local_user_cats_${cleanPhone.removePrefix("0")}", null)
                    val savedCats = if (!savedCatsStr.isNullOrEmpty()) savedCatsStr.split(",") else null

                    val localUser = KodyarUser(
                        id = "user_${cleanPhone}",
                        full_name = savedName ?: "کاربر کدیار",
                        phone = cleanPhone,
                        role = savedRole ?: "customer",
                        city = savedCity,
                        categories = savedCats,
                        is_approved = true,
                        approval_status = "approved",
                        is_verified = true,
                        isVerified = true
                    )
                    _currentUser.value = localUser
                    saveUserToCache(localUser)
                    saveSessionToken(localUser.id)
                    sharedPrefs.edit().remove("session_token").apply()
                    loadFreeStatus()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    _authError.value = "خطای اتصال به سرور: ${e.message}"
                    onResult(false, _authError.value)
                }
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun sendSms(phone: String, type: String, verificationCode: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        viewModelScope.launch {
            _isAuthLoading.value = true
            try {
                val res = repository.sendSms(cleanPhone, type, verificationCode)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    onResult(true, res.message ?: "پیامک با موفقیت ارسال شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "خطا در ارسال پیامک")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در ارسال پیامک: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun forgotPasswordRequest(phone: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.forgotPasswordRequest(cleanPhone)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    onResult(true, res.message ?: "کد بازیابی رمز عبور پیامک شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "شماره تلفن یافت نشد یا ثبت نشده است")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در برقراری ارتباط: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun forgotPasswordReset(phone: String, code: String, newPassword: String? = null, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        val cleanCode = normalizeDigitsAndTrim(code)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.forgotPasswordReset(cleanPhone, cleanCode, newPassword)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    onResult(true, res.message ?: "رمز عبور جدید با موفقیت ثبت شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "کد تایید یا رمز عبور نامعتبر است")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در ثبت رمز جدید: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun sendOtp(phone: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.sendOtp(cleanPhone)
                if (res.status == "ok" || res.status == "success" || res.message?.contains("ارسال") == true) {
                    onResult(true, res.message ?: "کد تایید با موفقیت پیامک شد")
                } else {
                    onResult(false, res.error ?: res.message ?: "خطا در ارسال کد پیامک")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در برقراری ارتباط: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun verifyOtp(phone: String, code: String, newPassword: String? = null, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = normalizePhone(phone)
        val cleanCode = normalizeDigitsAndTrim(code)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val res = repository.verifyOtp(cleanPhone, cleanCode, newPassword = newPassword)
                if (res.status == "ok" || res.status == "success" || res.success == true) {
                    if (res.user != null || !res.token.isNullOrBlank() || !res.session_token.isNullOrBlank()) {
                        val user = res.user?.copy(phone = cleanPhone, is_verified = true, isVerified = true) ?: KodyarUser(
                            id = "user_${cleanPhone}",
                            full_name = "کاربر کدیار",
                            phone = cleanPhone,
                            role = "customer",
                            is_verified = true,
                            isVerified = true
                        )
                        _currentUser.value = user
                        saveUserToCache(user)
                        val token = res.token ?: res.session_token ?: user.id
                        saveSessionToken(token)
                        loadFreeStatus()
                        loadRepairs()
                    }
                    onResult(true, res.message ?: "کد تایید شد و تغییرات اعمال گردید.")
                } else {
                    val errMsg = res.error ?: res.message ?: "کد تایید وارد شده نادرست یا منقضی شده است"
                    _authError.value = errMsg
                    onResult(false, errMsg)
                }
            } catch (e: Exception) {
                onResult(false, "خطا در تایید کد: ${e.localizedMessage}")
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun register(
        phone: String,
        pass: String,
        name: String,
        role: String = "customer",
        city: String? = null,
        categories: List<String>? = null,
        district: String? = null,
        documents: List<String>? = null,
        documentImages: List<String>? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cleanPhone = normalizePhone(phone)
        val cleanPass = normalizeDigitsAndTrim(pass)
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            try {
                val uploadedUrls = mutableListOf<String>()
                if (!documentImages.isNullOrEmpty()) {
                    for ((idx, imgData) in documentImages.withIndex()) {
                        if (imgData.isNotBlank()) {
                            if (imgData.startsWith("http://") || imgData.startsWith("https://")) {
                                uploadedUrls.add(imgData)
                            } else {
                                try {
                                    val uploadRes = repository.uploadFile("doc_${cleanPhone}_${idx}.jpg", imgData)
                                    val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
                                    val finalUrl = uploadRes.url ?: (if (uploadRes.id != null) "$baseUrl/uploads/${uploadRes.id}" else null)
                                    if (finalUrl != null) {
                                        uploadedUrls.add(finalUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e("AssistantViewModel", "Failed to upload document file: ${e.message}")
                                }
                            }
                        }
                    }
                }
                val finalDocImages = if (uploadedUrls.isNotEmpty()) uploadedUrls else documentImages
                val response = repository.register(
                    phone = cleanPhone,
                    pass = cleanPass,
                    name = name,
                    role = role,
                    city = city,
                    district = district,
                    categories = categories,
                    documents = documents,
                    documentImages = finalDocImages
                )
                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val sub = extractSubscription(response.user, response.subscription)
                    val userWithSub = response.user.copy(subscription = sub)
                    val finalUser = if (role == "technician") {
                        userWithSub.copy(
                            role = "technician",
                            city = city,
                            categories = categories,
                            district = district,
                            is_approved = false,
                            approval_status = "pending",
                            uploaded_documents = documents ?: emptyList()
                        )
                    } else {
                        userWithSub.copy(role = "customer", city = city)
                    }
                    val tokenToSave = response.token ?: response.session_token ?: response.user.id
                    _currentUser.value = finalUser
                    saveUserToCache(finalUser)
                    saveSessionToken(tokenToSave)
                    sharedPrefs.edit()
                        .remove("session_token")
                        .putString("local_user_pass_${cleanPhone}", hashPassword(cleanPass))
                        .putString("local_user_pass_${cleanPhone.removePrefix("0")}", hashPassword(cleanPass))
                        .putString("local_user_name_${cleanPhone}", name)
                        .putString("local_user_name_${cleanPhone.removePrefix("0")}", name)
                        .putString("local_user_role_${cleanPhone}", role)
                        .putString("local_user_role_${cleanPhone.removePrefix("0")}", role)
                        .putString("local_user_city_${cleanPhone}", city ?: "")
                        .putString("local_user_city_${cleanPhone.removePrefix("0")}", city ?: "")
                        .putString("local_user_cats_${cleanPhone}", categories?.joinToString(",") ?: "")
                        .putString("local_user_cats_${cleanPhone.removePrefix("0")}", categories?.joinToString(",") ?: "")
                        .apply()
                    
                    if (role == "technician") {
                        val techAvatar = documentImages?.firstOrNull { it.isNotBlank() }
                            ?: documents?.firstOrNull { it.isNotBlank() }
                            ?: finalUser.resolvedAvatarUrl
                        val newTech = KodyarTechnician(
                            id = finalUser.id,
                            name = finalUser.full_name,
                            city = city ?: "تهران",
                            isVerified = false,
                            completedOrders = 0,
                            bio = "تکنسین متخصص لوازم خانگی کدیار۲۴",
                            categories = categories ?: listOf("ماشین لباسشویی", "یخچال و فریزر"),
                            rating = 5.0,
                            satisfactionRate = 100,
                            image = techAvatar
                        )
                        _liveTechnicians.value = _liveTechnicians.value + newTech
                    }
                    
                    loadFreeStatus()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    val errMsg = response.error ?: response.message ?: "خطا در ثبت‌نام. شماره تلفن ممکن است قبلاً ثبت شده باشد."
                    _authError.value = errMsg
                    onResult(false, errMsg)
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "خطا در اتصال به سرور"
                _authError.value = errMsg
                onResult(false, errMsg)
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun checkTechnicianApprovalStatus(onResult: (Boolean, String) -> Unit) {
        val token = getSessionToken()
        val user = _currentUser.value
        if (token.isNullOrBlank()) {
            val approved = user?.isApprovedUser == true ||
                _liveTechnicians.value.any { (it.id == user?.id || (!it.name.isNullOrBlank() && it.name == user?.full_name)) && it.isVerified == true }
            onResult(approved, if (approved) "حساب شما تایید شده است." else "اطلاعات نشست کاربری یافت نشد. لطفاً مجدداً وارد شوید.")
            return
        }
        viewModelScope.launch {
            try {
                val response = repository.getMe(token)
                val dbResponse = try { repository.getKodyarDatabase() } catch (e: Exception) { null }
                val serverTechs = dbResponse?.technicians ?: _liveTechnicians.value
                if (dbResponse?.technicians != null) {
                    _liveTechnicians.value = dbResponse.technicians
                }

                if ((response.status == "ok" || response.status == "success") && response.user != null) {
                    val serverUser = response.user
                    val isApprovedOnServer = serverUser.isApprovedUser ||
                        serverTechs.any { (it.id == serverUser.id || (!it.name.isNullOrBlank() && it.name == serverUser.full_name)) && it.isVerified == true }
                    val sub = extractSubscription(serverUser, response.subscription)
                    val cached = getCachedUser()
                    val persistentCity = if (!serverUser.phone.isNullOrBlank()) sharedPrefs.getString("persistent_city_${serverUser.phone}", null) else null

                    val updatedUser = serverUser.copy(
                        subscription = sub,
                        city = if (!serverUser.city.isNullOrBlank()) serverUser.city else (cached?.city ?: persistentCity),
                        role = if (!serverUser.role.isNullOrBlank()) serverUser.role else (cached?.role ?: "customer"),
                        categories = if (!serverUser.categories.isNullOrEmpty()) serverUser.categories else cached?.categories,
                        is_approved = isApprovedOnServer,
                        approval_status = if (isApprovedOnServer) "approved" else "pending",
                        is_verified = isApprovedOnServer,
                        isVerified = isApprovedOnServer,
                        status = if (isApprovedOnServer) "verified" else (serverUser.status ?: "pending")
                    )
                    _currentUser.value = updatedUser
                    saveUserToCache(updatedUser)

                    if (isApprovedOnServer) {
                        onResult(true, "✅ تبریک! حساب شما توسط مدیریت سایت کدیار تایید شده است.")
                    } else {
                        onResult(false, "⏳ حساب شما هنوز توسط مدیریت سایت ممیزی و تایید نشده است. لطفاً منتظر بمانید.")
                    }
                } else {
                    val approved = user?.isApprovedUser == true ||
                        serverTechs.any { (it.id == user?.id || (!it.name.isNullOrBlank() && it.name == user?.full_name)) && it.isVerified == true }
                    if (approved && user != null) {
                        val updated = user.copy(
                            is_approved = true,
                            approval_status = "approved",
                            is_verified = true,
                            isVerified = true,
                            status = "verified"
                        )
                        _currentUser.value = updated
                        saveUserToCache(updated)
                    }
                    onResult(approved, if (approved) "✅ تبریک! حساب شما توسط مدیریت سایت کدیار تایید شده است." else "امکان استعلام وضعیت از سرور وجود نداشت. وضعیت فعلی: ${if (approved) "تایید شده" else "در انتظار تایید"}")
                }
            } catch (e: Exception) {
                val approved = user?.isApprovedUser == true ||
                    _liveTechnicians.value.any { (it.id == user?.id || (!it.name.isNullOrBlank() && it.name == user?.full_name)) && it.isVerified == true }
                if (approved && user != null) {
                    val updated = user.copy(
                        is_approved = true,
                        approval_status = "approved",
                        is_verified = true,
                        isVerified = true,
                        status = "verified"
                    )
                    _currentUser.value = updated
                    saveUserToCache(updated)
                }
                onResult(approved, if (approved) "✅ تبریک! حساب شما توسط مدیریت سایت کدیار تایید شده است." else "خطا در اتصال به سرور: ${e.message}")
            }
        }
    }

    fun updateTechnicianApprovalStatus(isApproved: Boolean) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(
            is_approved = isApproved,
            approval_status = if (isApproved) "approved" else "pending"
        )
        _currentUser.value = updatedUser
        saveUserToCache(updatedUser)
    }

    fun uploadTechnicianDocuments(docs: List<String>) {
        val user = _currentUser.value ?: return
        val currentDocs = (user.uploaded_documents ?: emptyList()) + docs
        val updatedUser = user.copy(
            uploaded_documents = currentDocs.distinct()
        )
        _currentUser.value = updatedUser
        saveUserToCache(updatedUser)
    }

    fun logout() {
        _currentUser.value = null
        clearSessionToken()
        clearUserCache()
        _repairOrders.value = emptyList()
        _freeErrorCount.value = 0
        _freeProblemCount.value = 0
    }

    // --- Load Kodyar Database ---
    fun loadKodyarDatabase() {
        viewModelScope.launch {
            // Only trigger database loading splash screen if we don't have any cached/loaded data yet!
            _isDatabaseLoading.value = _liveErrorCodes.value.isEmpty()
            try {
                val response = repository.getKodyarDatabase()
                checkForDatabaseUpdates(response)
                checkAppVersion(response)
                _liveErrorCodes.value = response.resolvedErrorCodes
                _liveSpareParts.value = response.resolvedSpareParts
                
                val incomingTechs = response.resolvedTechnicians
                val currentTechs = _liveTechnicians.value.toMutableList()
                if (incomingTechs.isNotEmpty()) {
                    for (t in incomingTechs) {
                        val idx = currentTechs.indexOfFirst { it.id == t.id || (!it.name.isNullOrBlank() && it.name == t.name) }
                        if (idx >= 0) {
                            currentTechs[idx] = t
                        } else {
                            currentTechs.add(t)
                        }
                    }
                }
                _liveTechnicians.value = currentTechs

                try {
                    val directTechs = repository.getTechniciansDirectly()
                    if (directTechs.isNotEmpty()) {
                        val updatedList = _liveTechnicians.value.toMutableList()
                        for (t in directTechs) {
                            val idx = updatedList.indexOfFirst { it.id == t.id || (!it.name.isNullOrBlank() && it.name == t.name) }
                            if (idx >= 0) {
                                updatedList[idx] = t
                            } else {
                                updatedList.add(t)
                            }
                        }
                        _liveTechnicians.value = updatedList
                    }
                } catch (_: Exception) {}

                val currentTechUser = _currentUser.value
                if (currentTechUser != null && currentTechUser.role == "technician" && currentTechUser.isApprovedUser != true) {
                    val matchedTech = _liveTechnicians.value.find { it.id == currentTechUser.id || (!it.name.isNullOrBlank() && it.name == currentTechUser.full_name) }
                    if (matchedTech?.resolvedIsVerified == true) {
                        val updatedTech = currentTechUser.copy(
                            is_approved = true,
                            approval_status = "approved",
                            is_verified = true,
                            isVerified = true,
                            status = "verified"
                        )
                        _currentUser.value = updatedTech
                        saveUserToCache(updatedTech)
                    }
                }
                _liveCommonProblems.value = response.resolvedCommonProblems

                val dynamicCats = (response.resolvedCategoriesList + response.resolvedErrorCodes.mapNotNull { it.resolvedCategory }).filter { it.isNotBlank() }.distinct()
                _liveCategories.value = listOf("همه") + dynamicCats

                val dynamicBrands = (response.resolvedBrandsList + response.resolvedErrorCodes.mapNotNull { it.brand }).filter { it.isNotBlank() }.distinct()
                _liveBrands.value = listOf("همه") + dynamicBrands
                val parsedCities = response.resolvedCitiesList.flatMap { 
                    listOfNotNull(it.name, it.title, it.city, it.cityName, it.name_fa, it.nameFarsi, it.slug)
                }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                _liveCities.value = listOf("همه") + parsedCities
                _liveCitiesStructured.value = response.resolvedCitiesList

                // Trigger initial search results
                updateSearchFilters(_searchQuery.value, _selectedBrand.value, _selectedCategory.value)

                hasLoadedFromNetwork = true

                // Save to Room Database as Single Source of Truth
                try {
                    repository.saveDatabaseToRoom(
                        errorCodes = response.resolvedErrorCodes,
                        spareParts = response.resolvedSpareParts,
                        commonProblems = response.resolvedCommonProblems,
                        technicians = response.resolvedTechnicians
                    )
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Failed to save database response to Room DB", e)
                }

                // Save successful response to local cache
                try {
                    val json = databaseAdapter.toJson(response)
                    sharedPrefs.edit().putString("cached_kodyar_database", json).apply()
                    Log.d("AssistantViewModel", "Saved database response to local cache.")
                } catch (e: Exception) {
                    Log.e("AssistantViewModel", "Failed to cache database response.", e)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error loading kodyar database from API.", e)
            } finally {
                ensureDefaultFilters()
                _isDatabaseLoading.value = false
            }
        }
    }

    fun refreshTechnicians() {
        viewModelScope.launch {
            try {
                val techRes = repository.getTechniciansDirectly()
                if (techRes.isNotEmpty()) {
                    val currentList = _liveTechnicians.value.toMutableList()
                    for (t in techRes) {
                        val idx = currentList.indexOfFirst { it.id == t.id || (!it.name.isNullOrBlank() && it.name == t.name) }
                        if (idx >= 0) {
                            currentList[idx] = t
                        } else {
                            currentList.add(t)
                        }
                    }
                    _liveTechnicians.value = currentList
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to refresh technicians: ${e.message}")
            }
        }
    }

    private fun matchNormalized(target: String?, filter: String?): Boolean {
        val normTarget = normalizePersian(target)
        val normFilter = normalizePersian(filter)
        if (normFilter == "همه" || normFilter.isEmpty()) return true
        return normTarget == normFilter || normTarget.contains(normFilter, ignoreCase = true) || normFilter.contains(normTarget, ignoreCase = true)
    }

    fun getAvailableModelsFor(brand: String, category: String): List<String> {
        val codes = _liveErrorCodes.value
        if (codes.isEmpty()) return listOf("همه")

        val filtered = codes.filter { error ->
            matchNormalized(error.brand, brand) && matchNormalized(error.category, category)
        }

        val models = filtered.mapNotNull { error ->
            val modelName = error.model?.trim()
            if (!modelName.isNullOrEmpty()) {
                modelName
            } else {
                val title = error.title?.trim()
                val code = error.code?.trim()
                if (!title.isNullOrEmpty() && title != code) {
                    title
                } else {
                    null
                }
            }
        }.distinct().sorted()

        return listOf("همه") + models
    }

    fun canonicalModel(input: String?): String {
        if (input == null) return ""
        var text = input.lowercase()
        
        // 1. Convert Persian/Arabic digits to English digits
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in 0..9) {
            text = text.replace(persianDigits[i].toString(), i.toString())
            text = text.replace(arabicDigits[i].toString(), i.toString())
        }
        
        // 2. Map common Persian words for English letters
        val replacements = listOf(
            "دبلیو" to "w",
            "ایکس" to "x",
            "کیو" to "q",
            "وای" to "y",
            "اچ" to "h",
            "اس" to "s",
            "ام" to "m",
            "ال" to "l",
            "اف" to "f",
            "پی" to "p",
            "جی" to "g",
            "ار" to "r",
            "زد" to "z",
            "وی" to "v",
            "تی" to "t",
            "سی" to "c",
            "دی" to "d",
            "بی" to "b",
            "کی" to "k",
            "ای" to "e",
            "ان" to "n",
            "یو" to "u",
            "ب" to "b",
            "پ" to "p",
            "ت" to "t",
            "ج" to "j",
            "د" to "d",
            "ر" to "r",
            "س" to "s",
            "ف" to "f",
            "ک" to "k",
            "ل" to "l",
            "م" to "m",
            "ن" to "n",
            "و" to "v",
            "ه" to "h",
            "ی" to "y"
        )
        
        for ((persian, english) in replacements) {
            text = text.replace(persian, english)
        }
        
        // 3. Remove all non-alphanumeric characters
        return text.replace("[^a-zA-Z0-9]".toRegex(), "")
    }

    fun matchModelCanonical(error: KodyarErrorCode, modelQuery: String): Boolean {
        if (modelQuery.isEmpty() || modelQuery == "همه") return true
        val canonicalQuery = canonicalModel(modelQuery)
        if (canonicalQuery.isEmpty()) return true
        
        // Check error.model
        val modelCanonical = canonicalModel(error.model)
        if (modelCanonical.contains(canonicalQuery) || canonicalQuery.contains(modelCanonical)) {
            return true
        }
        
        // Check error.title
        val titleCanonical = canonicalModel(error.title)
        if (titleCanonical.contains(canonicalQuery) || canonicalQuery.contains(titleCanonical)) {
            return true
        }
        
        // Check error.code
        val codeCanonical = canonicalModel(error.code)
        if (codeCanonical.contains(canonicalQuery) || canonicalQuery.contains(codeCanonical)) {
            return true
        }

        // Check error.description
        val descCanonical = canonicalModel(error.description)
        if (descCanonical.contains(canonicalQuery) || canonicalQuery.contains(descCanonical)) {
            return true
        }
        
        return false
    }

    fun updateSearchFilters(query: String, brand: String, category: String, model: String = _modelQuery.value) {
        _searchQuery.value = query
        _selectedBrand.value = brand
        _selectedCategory.value = category
        _modelQuery.value = model

        _searchResults.value = _liveErrorCodes.value.filter { error ->
            val isSavedMatch = if (_showOnlySaved.value) {
                savedErrors.value.any {
                    it.code == error.code && it.brand == error.brand && it.category == error.category
                }
            } else {
                true
            }

            val matchQuery = if (query.isEmpty()) {
                true
            } else {
                val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    true
                } else {
                    words.all { word ->
                        error.resolvedCode.contains(word, ignoreCase = true) ||
                        (error.brand ?: "").contains(word, ignoreCase = true) ||
                        error.resolvedCategory.contains(word, ignoreCase = true) ||
                        error.resolvedTitle.contains(word, ignoreCase = true) ||
                        (error.description ?: "").contains(word, ignoreCase = true)
                    }
                }
            }

            val matchBrand = matchNormalized(error.brand, brand)
            val matchCategory = matchNormalized(error.category ?: error.resolvedCategory, category)

            val matchModel = model.isEmpty() || model == "همه" || matchModelCanonical(error, model)

            isSavedMatch && matchQuery && matchBrand && matchCategory && matchModel
        }
    }

    private fun checkForDatabaseUpdates(response: KodyarDatabaseResponse) {
        val oldErrorIds = sharedPrefs.getStringSet("known_error_ids", emptySet()) ?: emptySet()
        val oldProblemIds = sharedPrefs.getStringSet("known_problem_ids", emptySet()) ?: emptySet()
        val oldPartIds = sharedPrefs.getStringSet("known_part_ids", emptySet()) ?: emptySet()

        val newErrorIds = response.errorCodes?.mapNotNull { it.id }.orEmpty().toSet()
        val newProblemIds = response.commonProblems?.mapNotNull { it.id }.orEmpty().toSet()
        val newPartIds = response.spareParts?.mapNotNull { it.id }.orEmpty().toSet()

        if (oldErrorIds.isEmpty() && oldProblemIds.isEmpty() && oldPartIds.isEmpty()) {
            // First run, silently save the list
            sharedPrefs.edit()
                .putStringSet("known_error_ids", newErrorIds)
                .putStringSet("known_problem_ids", newProblemIds)
                .putStringSet("known_part_ids", newPartIds)
                .apply()
        } else {
            // Check for added/new items
            val addedErrors = response.errorCodes?.filter { it.id != null && !oldErrorIds.contains(it.id) }.orEmpty()
            val addedProblems = response.commonProblems?.filter { it.id != null && !oldProblemIds.contains(it.id) }.orEmpty()
            val addedParts = response.spareParts?.filter { it.id != null && !oldPartIds.contains(it.id) }.orEmpty()

            if (addedErrors.isNotEmpty() || addedProblems.isNotEmpty() || addedParts.isNotEmpty()) {
                _appUpdateNotification.value = AppUpdateNotification(
                    newErrors = addedErrors,
                    newProblems = addedProblems,
                    newParts = addedParts
                )

                // Persist updated sets
                val updatedErrorIds = oldErrorIds.toMutableSet().apply { addAll(newErrorIds) }
                val updatedProblemIds = oldProblemIds.toMutableSet().apply { addAll(newProblemIds) }
                val updatedPartIds = oldPartIds.toMutableSet().apply { addAll(newPartIds) }

                sharedPrefs.edit()
                    .putStringSet("known_error_ids", updatedErrorIds)
                    .putStringSet("known_problem_ids", updatedProblemIds)
                    .putStringSet("known_part_ids", updatedPartIds)
                    .apply()
            }
        }
    }



    // --- Shorthand Helper to parse Causes / Steps ---
    fun Any?.toListOfStrings(): List<String> {
        if (this == null) return emptyList()
        if (this is List<*>) {
            return this.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        }
        val str = this.toString().trim()
        if (str.isEmpty()) return emptyList()
        return str.split(Regex("[\r\n؛•\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    // --- Bookmarked / Saved Errors ---

    fun toggleSavedError(code: String, brand: String, category: String, isCurrentlySaved: Boolean) {
        viewModelScope.launch {
            repository.toggleSavedError(code, brand, category, isCurrentlySaved)
        }
    }

    fun isErrorSaved(code: String, brand: String, category: String): Flow<Boolean> {
        return repository.isErrorSaved(code, brand, category)
    }

    // --- Custom / Notes Errors ---
    val customErrors: StateFlow<List<CustomErrorEntity>> = repository.allCustomErrors
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCustomError(code: String, brand: String, category: String, description: String, solution: String, severity: String, userNote: String) {
        viewModelScope.launch {
            val custom = CustomErrorEntity(
                code = code,
                brand = brand,
                category = category,
                description = description,
                solution = solution,
                severity = severity,
                userNote = userNote
            )
            repository.addCustomError(custom)
        }
    }

    fun deleteCustomError(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomError(id)
        }
    }

    fun updateCustomErrorNote(id: Long, note: String) {
        viewModelScope.launch {
            repository.updateCustomErrorNote(id, note)
        }
    }

    // --- Cart functions ---
    private fun persistCart() {
        try {
            val cartList = _cart.value
            val qtyMap = _cartQty.value
            val cartJson = moshi.adapter<List<String>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            ).toJson(cartList)
            val qtyJson = moshi.adapter<Map<String, Int>>(
                com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Int::class.javaObjectType)
            ).toJson(qtyMap)

            sharedPrefs.edit()
                .putString("persisted_cart_items", cartJson)
                .putString("persisted_cart_quantities", qtyJson)
                .apply()
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error persisting cart", e)
        }
    }

    private fun loadPersistedCart() {
        try {
            val cartJson = sharedPrefs.getString("persisted_cart_items", null)
            val qtyJson = sharedPrefs.getString("persisted_cart_quantities", null)
            if (!cartJson.isNullOrEmpty()) {
                val list = moshi.adapter<List<String>>(
                    com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
                ).fromJson(cartJson)
                if (list != null) {
                    _cart.value = list
                }
            }
            if (!qtyJson.isNullOrEmpty()) {
                val map = moshi.adapter<Map<String, Int>>(
                    com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Int::class.javaObjectType)
                ).fromJson(qtyJson)
                if (map != null) {
                    _cartQty.value = map
                }
            }
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Error loading persisted cart", e)
        }
    }

    fun addToCart(partId: String) {
        val currentCart = _cart.value.toMutableList()
        if (!currentCart.contains(partId)) {
            currentCart.add(partId)
            _cart.value = currentCart
        }
        val currentQty = _cartQty.value.toMutableMap()
        currentQty[partId] = currentQty[partId] ?: 1
        _cartQty.value = currentQty
        persistCart()
    }

    fun addToCartWithQty(partId: String, qty: Int) {
        val part = _liveSpareParts.value.find { it.id == partId }
        val maxStock = part?.stock ?: 10
        if (maxStock <= 0) return

        val currentCart = _cart.value.toMutableList()
        if (!currentCart.contains(partId)) {
            currentCart.add(partId)
            _cart.value = currentCart
        }
        val currentQty = _cartQty.value.toMutableMap()
        val existing = currentQty[partId] ?: 0
        currentQty[partId] = minOf(maxStock, (existing + qty).coerceAtLeast(1))
        _cartQty.value = currentQty
        persistCart()
    }

    fun removeFromCart(partId: String) {
        val currentCart = _cart.value.toMutableList()
        currentCart.remove(partId)
        _cart.value = currentCart

        val currentQty = _cartQty.value.toMutableMap()
        currentQty.remove(partId)
        _cartQty.value = currentQty
        persistCart()
    }

    fun updateCartQty(partId: String, qty: Int) {
        val part = _liveSpareParts.value.find { it.id == partId }
        val maxStock = part?.stock ?: 10
        val currentQty = _cartQty.value.toMutableMap()
        if (qty > 0) {
            currentQty[partId] = minOf(maxStock, qty)
            _cartQty.value = currentQty
            persistCart()
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _cartQty.value = emptyMap()
        _purchaseSuccess.value = false
        persistCart()
    }

    /**
     * Submits every item currently in the cart to the server's dedicated
     * `api/store/purchase-part` endpoint, one request per line item, so each purchase
     * lands in the customer's website profile AND the admin panel's purchase list for
     * manual approval — exactly like the website's card-to-card checkout flow.
     *
     * Replaces the previous implementation, which only saved the order in local
     * SharedPreferences (invisible to the server/admin) and then redirected the user to a
     * non-existent WooCommerce cart URL, making part purchases from the app a dead end.
     *
     * @param cardHolder Full name of the person who deposited the money (as printed on the bank card).
     * @param trackNumber The bank transfer tracking/reference number from the deposit receipt.
     * @param address Delivery address for the purchased part(s).
     * @param onResult Callback invoked with `(success, errorMessage)` once all items are processed.
     */
    fun submitPartPurchaseOrdersToServer(
        cardHolder: String = "",
        trackNumber: String = "",
        address: String = "",
        onResult: (Boolean, String?) -> Unit
    ) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        val finalCardHolder = if (cardHolder.isBlank()) "پرداخت آنلاین سایت" else cardHolder
        val finalTrackNumber = if (trackNumber.isBlank()) "همگام‌سازی وب‌سایت" else trackNumber
        val finalAddress = if (address.isBlank()) (_currentUser.value?.city ?: "آدرس ثبت‌شده در حساب") else address

        viewModelScope.launch {
            _isPurchaseLoading.value = true
            try {
                val user = _currentUser.value
                val cartList = _cart.value.toList()
                val qtyMap = _cartQty.value.toMap()
                val failedParts = mutableListOf<String>()

                for (partId in cartList) {
                    val part = _liveSpareParts.value.find { it.id == partId } ?: continue
                    val qty = qtyMap[partId] ?: 1
                    val price = part.price ?: 0.0
                    val subtotal = price * qty

                    try {
                        // 1. Send store purchase request to backend API /api/store/order or /api/store/purchase
                        repository.purchasePart(
                            token = token,
                            partId = partId,
                            partName = part.name ?: "قطعه یدکی",
                            quantity = qty,
                            unitPrice = price,
                            totalPrice = subtotal,
                            address = finalAddress,
                            city = user?.city,
                            notes = "پرداخت آنلاین کارت - کد پیگیری: $finalTrackNumber",
                            customerName = user?.full_name,
                            customerPhone = user?.phone
                        )

                        val order = PartPurchaseOrder(
                            id = "order_${System.currentTimeMillis()}_${partId}",
                            partId = partId,
                            partName = part.name ?: "قطعه یدکی",
                            quantity = qty,
                            unitPrice = price,
                            totalPrice = subtotal,
                            address = finalAddress,
                            notes = "در انتظار تایید پرداخت سایت",
                            dateStr = getCurrentPersianDate(),
                            status = "pending_payment"
                        )
                        savePartPurchase(order)
                    } catch (e: Exception) {
                        Log.e("AssistantViewModel", "Error submitting purchase for part $partId", e)
                        failedParts.add(part.name ?: partId)
                    }
                }

                if (failedParts.isEmpty()) {
                    _purchaseSuccess.value = true
                    clearCart()
                    loadKodyarDatabase()
                    loadRepairs()
                    onResult(true, null)
                } else {
                    onResult(false, "ثبت سفارش برای این قطعات ناموفق بود: ${failedParts.joinToString("، ")}")
                }
            } catch (e: Exception) {
                onResult(false, "خطا در ثبت سفارش: ${e.message}")
            } finally {
                _isPurchaseLoading.value = false
            }
        }
    }

    fun openPartPurchaseWebUrl(context: Context, partId: String, userPhone: String?, paymentUrl: String? = null) {
        val targetUrl = if (!paymentUrl.isNullOrBlank()) {
            paymentUrl.trim()
        } else {
            val cleanPartId = Uri.encode(partId.trim())
            val cleanPhone = Uri.encode((userPhone ?: "").trim())
            val baseUrl = com.example.data.api.KodyarRetrofitClient.siteRootUrl
            "$baseUrl/?action=buy_part&partId=$cleanPartId&phone=$cleanPhone"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در باز کردن مرورگر جهت خرید قطعه", Toast.LENGTH_SHORT).show()
        }
    }

    fun initiateDirectPartPurchase(
        context: Context,
        partId: String,
        partName: String,
        quantity: Int = 1,
        totalPrice: Double = 0.0
    ) {
        val user = _currentUser.value
        if (user == null || user.phone.isNullOrBlank()) {
            Toast.makeText(context, "لطفاً ابتدا وارد حساب کاربری خود شوید.", Toast.LENGTH_LONG).show()
            return
        }
        val phone = user.phone ?: ""
        val token = getSessionToken() ?: ""

        viewModelScope.launch {
            try {
                val part = _liveSpareParts.value.find { it.id == partId }
                val unitPrice = part?.price ?: (if (quantity > 0) totalPrice / quantity else 0.0)
                val finalTotal = if (totalPrice > 0) totalPrice else (unitPrice * quantity)

                // Send store purchase request to backend API /api/store/order or /api/store/purchase
                val resp = repository.purchasePart(
                    token = token,
                    partId = partId,
                    partName = partName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalPrice = finalTotal,
                    address = user.city ?: "ثبت شده از اپلیکیشن",
                    city = user.city,
                    notes = "خرید مستقیم از اپلیکیشن کدیار۲۴",
                    customerName = user.full_name,
                    customerPhone = phone
                )

                val effectiveDate = resp.shamsi_date ?: resp.shamsiDate ?: getCurrentPersianDate()
                val paymentUrl = resp.payment_url ?: resp.paymentUrl
                val orderIdStr = resp.order_id?.toString() ?: resp.orderId?.toString() ?: "order_${System.currentTimeMillis()}_${partId}"

                // Save purchase order locally in Room DB
                val localOrder = PartPurchaseOrder(
                    id = orderIdStr,
                    partId = partId,
                    partName = partName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalPrice = finalTotal,
                    address = user.city ?: "ثبت شده از اپلیکیشن",
                    notes = "خرید مستقیم - ثبت شده در سایت",
                    dateStr = effectiveDate,
                    status = "pending",
                    paymentUrl = paymentUrl,
                    shamsiDate = resp.shamsi_date ?: resp.shamsiDate
                )
                savePartPurchase(localOrder)

                withContext(Dispatchers.Main) {
                    val msg = resp.message ?: "سفارش قطعه در دیتابیس ثبت شد. در حال انتقال به درگاه پرداخت..."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    openPartPurchaseWebUrl(context, partId, phone, paymentUrl)
                }

            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error submitting store purchase API: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "در حال انتقال به صفحه پرداخت...", Toast.LENGTH_SHORT).show()
                }
                openPartPurchaseWebUrl(context, partId, phone, null)
            }
        }
    }

    private fun isPartPurchase(order: com.example.data.model.KodyarRepairOrder): Boolean {
        val desc = order.description?.trim() ?: ""
        val status = order.status?.trim() ?: ""
        val techId = order.technician_id?.trim() ?: ""
        
        if (status == "sent" || status == "delivered") return true
        if (desc.contains("خرید") || desc.contains("قطعه") || desc.contains("فروشگاه") || desc.contains("فیش")) {
            if (techId.isEmpty() || techId == "null") return true
        }
        return false
    }

    // --- Repair Orders ---
    fun loadRepairs() {
        val token = getSessionToken() ?: return
        viewModelScope.launch {
            _isRepairsLoading.value = true
            try {
                val user = _currentUser.value
                val resp = repository.getRepairs(token)

                val allOrders = ((resp.repairs ?: emptyList()) +
                        (resp.repair_requests ?: emptyList()) +
                        (resp.orders ?: emptyList()) +
                        (resp.data ?: emptyList())).distinctBy { it.id ?: "${it.description}_${it.created_at}" }

                // Classify orders into Repair Requests vs Part Purchases dynamically
                val rawRepairs = allOrders.filter { !isPartPurchase(it) }
                val rawPurchases = allOrders.filter { isPartPurchase(it) }

                // 1. Repair orders
                val filtered = if (user != null && user.role == "technician") {
                    rawRepairs.filter { order ->
                        val isAssignedToMe = (order.technician_id != null && order.technician_id == user.id) ||
                                (!order.technician_name.isNullOrBlank() && user.full_name != null && order.technician_name == user.full_name) ||
                                _liveTechnicians.value.any { tech ->
                                    (tech.id == user.id || (!tech.name.isNullOrBlank() && tech.name == user.full_name)) &&
                                    (!order.technician_id.isNullOrBlank() && tech.id == order.technician_id)
                                }
                        val isCreatedByMe = (order.user_id != null && order.user_id == user.id) ||
                                (!user.phone.isNullOrBlank() && (order.user_phone == user.phone || order.customer_phone == user.phone || order.description?.contains(user.phone!!) == true))
                        val orderCity = if (!order.city.isNullOrBlank()) order.city else user.city
                        val cityMatches = areCitiesCompatible(orderCity, user.city)
                        val isUnassigned = order.technician_id.isNullOrBlank() || order.technician_id == "null"

                        isAssignedToMe || isCreatedByMe || (isUnassigned && cityMatches)
                    }
                } else {
                    rawRepairs
                }
                _repairOrders.value = filtered

                // 2. Part Purchases (from response.data if present, mapped to PartPurchaseOrder)
                val mappedPurchases = rawPurchases.map { order ->
                    PartPurchaseOrder(
                        id = order.id ?: "order_${System.currentTimeMillis()}_${order.id.hashCode()}",
                        partId = "",
                        partName = order.description ?: "سفارش قطعه",
                        quantity = 1,
                        unitPrice = 0.0,
                        totalPrice = 0.0,
                        address = order.city ?: "",
                        notes = "ثبت شده در سایت",
                        dateStr = order.resolvedDate.ifBlank { getCurrentPersianDate() },
                        status = order.status ?: "pending",
                        shamsiDate = order.shamsi_date ?: order.shamsiDate
                    )
                }
                
                // Merge server-fetched purchases with existing local purchases so local order details are retained while status is refreshed
                val currentLocal = _partPurchases.value
                val updatedLocal = currentLocal.map { local ->
                    val matchingServer = mappedPurchases.find { s ->
                        (s.id.isNotBlank() && s.id == local.id) ||
                        (s.partName.isNotBlank() && (s.partName == local.partName || s.partName.contains(local.partName)))
                    }
                    if (matchingServer != null) {
                        local.copy(
                            status = matchingServer.status ?: local.status,
                            shamsiDate = matchingServer.shamsiDate ?: local.shamsiDate
                        )
                    } else {
                        local
                    }
                }
                val newFromServer = mappedPurchases.filter { s ->
                    currentLocal.none { local ->
                        (s.id.isNotBlank() && s.id == local.id) ||
                        (s.partName.isNotBlank() && (s.partName == local.partName || s.partName.contains(local.partName)))
                    }
                }
                val mergedPurchases = updatedLocal + newFromServer

                _partPurchases.value = mergedPurchases
                try {
                    val json = partPurchasesAdapter.toJson(mergedPurchases)
                    sharedPrefs.edit().putString("part_purchases_json", json).apply()
                } catch (ex: java.lang.Exception) {
                    Log.e("AssistantViewModel", "Error updating local part purchases cache", ex)
                }
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error fetching repairs", e)
            } finally {
                _isRepairsLoading.value = false
            }
        }
    }

    fun submitRepairRequest(
        techId: String,
        description: String,
        city: String,
        appliance: String = "عمومی",
        brand: String = "عمومی",
        model: String? = null,
        errorCode: String? = null,
        customerName: String? = null,
        customerPhone: String? = null,
        address: String? = null,
        region: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        val user = _currentUser.value
        val tech = _liveTechnicians.value.find { it.id == techId }
        if (user != null && tech != null) {
            if (!areCitiesCompatible(user.city, tech.city)) {
                onResult(false, "شما فقط می‌توانید از تکنسین‌های فعال در محدوده خود درخواست کار ثبت کنید")
                return
            }
        }

        viewModelScope.launch {
            try {
                val response = repository.createRepair(
                    token = token,
                    city = if (city.isNotBlank()) city else (user?.city ?: "عمومی"),
                    appliance = if (appliance.isNotBlank()) appliance else "عمومی",
                    brand = if (brand.isNotBlank()) brand else "عمومی",
                    model = model,
                    problemDescription = description,
                    errorCode = errorCode,
                    customerName = customerName ?: user?.full_name,
                    customerPhone = customerPhone ?: user?.phone,
                    address = address,
                    technicianId = techId,
                    region = region ?: user?.province ?: user?.city
                )
                if (response.status == "ok" || response.status == "success") {
                    loadRepairs()
                    onResult(true, null)
                } else {
                    onResult(false, response.error ?: response.message ?: "خطا در ثبت درخواست تعمیرکار")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط به سرور: ${e.message}")
            }
        }
    }

    fun updateUserProfile(fullName: String?, city: String?, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) {
            onResult(false, "نشست کاربری یافت نشد.")
            return
        }
        viewModelScope.launch {
            try {
                val resp = repository.updateProfile(token, fullName, city)
                if (resp.status == "ok" || resp.status == "success") {
                    val user = _currentUser.value
                    if (user != null) {
                        val updated = user.copy(
                            full_name = fullName ?: user.full_name,
                            city = city ?: user.city
                        )
                        _currentUser.value = updated
                        saveUserToCache(updated)
                    }
                    onResult(true, null)
                } else {
                    onResult(false, resp.error ?: "خطا در بروزرسانی پروفایل")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط با سرور: ${e.message}")
            }
        }
    }

    fun updateOrderStatus(orderId: String, status: String, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) {
            onResult(false, "نشست کاربری یافت نشد.")
            return
        }
        viewModelScope.launch {
            try {
                val resp = repository.updateOrderStatus(token, orderId, status)
                if (resp.status == "ok" || resp.status == "success") {
                    loadRepairs()
                    onResult(true, null)
                } else {
                    onResult(false, resp.error ?: "خطا در تغییر وضعیت سفارش")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط با سرور: ${e.message}")
            }
        }
    }

    // --- Subscription Card Verification ---
    fun submitCardVerify(cardHolder: String, trackNumber: String, productId: String, onResult: (Boolean, String?) -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onResult(false, "باید وارد حساب خود شوید")
            return
        }

        viewModelScope.launch {
            _isCardVerifyLoading.value = true
            try {
                val response = repository.verifyCard(token, cardHolder, trackNumber, productId)
                if (response.status == "ok" || response.status == "success") {
                    _cardVerifySuccess.value = true
                    checkSavedSession() // Refreshes premium status
                    onResult(true, null)
                } else {
                    onResult(false, response.error ?: "خطا در ثبت فیش پرداخت")
                }
            } catch (e: Exception) {
                onResult(false, "خطای ارتباط به سرور: ${e.message}")
            } finally {
                _isCardVerifyLoading.value = false
            }
        }
    }

    fun resetCardVerifySuccess() {
        _cardVerifySuccess.value = false
    }

    // --- Free usages ---
    fun loadFreeStatus() {
        val token = getSessionToken() ?: return
        viewModelScope.launch {
            try {
                val response = repository.getFreeStatus(token)
                _freeErrorCount.value = response.error_count ?: 0
                _freeProblemCount.value = response.problem_count ?: 0
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error loading free status", e)
            }
        }
    }

    fun useFreeCount(type: String, onComplete: () -> Unit) {
        val token = getSessionToken()
        if (token == null) {
            onComplete()
            return
        }
        viewModelScope.launch {
            try {
                val response = repository.useFree(token, type)
                _freeErrorCount.value = response.error_count ?: _freeErrorCount.value
                _freeProblemCount.value = response.problem_count ?: _freeProblemCount.value
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error posting free use", e)
            } finally {
                onComplete()
            }
        }
    }

    // --- AI Smart Repair Assistant Conversation State ---
    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<MessageEntity>> = _currentConversationId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessages(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dedicated System Instruction for Home Appliance Repair Expert AI
    private val repairAssistantSystemInstruction = """
        شما یک تعمیرکار فوق‌العاده با‌تجربه، دقیق، دلسوز و حرفه‌ای لوازم خانگی به نام کدیار۲۴ (Codyar24) هستید. 
        شما باید به زبان فارسی صمیمی، روان، مودبانه و بسیار فنی، کاربران و تعمیرکاران را برای عیب‌یابی و حل مشکلات لوازم خانگی مانند ماشین لباسشویی، یخچال، فریزر، ماشین ظرفشویی، کولر گازی، اسپلیت، جاروبرقی، مایکروویو و سایر تجهیزات هدایت کنید.
        
        لطفاً هنگام پاسخ دادن به هر سوال عیب‌یابی:
        1. علت‌های احتمالی (مثلاً خرابی قطعه، اتصالات، کثیفی فیلترها و غیره) را به صورت لیست‌وار و خوانا بنویسید.
        2. مراحل گام‌به‌گام برای تست و رفع عیب ارائه دهید.
        3. نکات ایمنی (مانند کشیدن دوشاخه از برق قبل از هر تستی) را حتماً متذکر شوید.
        4. در صورت نیاز به بررسی تخصصی‌تر، بگویید که نیاز به تکنسین مجاز است.
        پاسخ‌های شما باید کاملاً متمرکز بر حل مشکل و کاربردی باشند.
    """.trimIndent()

    fun selectConversation(id: Long?) {
        _currentConversationId.value = id
        _errorMessage.value = null
    }

    fun startNewConversation() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val conversationId = repository.createConversation("گفتگوی جدید تعمیراتی", "Codyar24")
                _currentConversationId.value = conversationId
                
                // Seed custom welcoming message in Persian
                val welcome = MessageEntity(
                    conversationId = conversationId,
                    role = "model",
                    text = "سلام! من کدیار۲۴ دستیار هوشمند و تخصصی تعمیرات لوازم خانگی شما هستم. دستگاه شما چه مشکلی دارد؟ یا به دنبال کدام کد خطا هستید؟ برند و مشکل آن را مطرح کنید تا با هم عیب‌یابی کنیم."
                )
                // Use sendMessage or a custom direct insertion
            } catch (e: Exception) {
                _errorMessage.value = "خطا در شروع گفتگو: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            if (_currentConversationId.value == id) {
                _currentConversationId.value = null
            }
            repository.deleteConversation(id)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            _currentConversationId.value = null
            repository.clearAllConversations()
        }
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                var convId = _currentConversationId.value

                // If no active conversation, create one dynamically
                if (convId == null) {
                    val generatedTitle = if (trimmedText.length > 25) {
                        "${trimmedText.take(22)}..."
                    } else {
                        trimmedText
                    }
                    convId = repository.createConversation(generatedTitle, "Codyar24")
                    _currentConversationId.value = convId
                }

                if (convId != null) {
                    val result = repository.sendMessage(
                        conversationId = convId,
                        userText = trimmedText,
                        systemInstructionText = repairAssistantSystemInstruction
                    )
                    
                    result.onFailure { exception ->
                        _errorMessage.value = exception.localizedMessage ?: "خطای ارتباط با سرور هوش مصنوعی"
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "خطا در ارسال پیام: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun setupNetworkCallback() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("AssistantViewModel", "Internet connected dynamically! Refreshing database immediately...")
                    viewModelScope.launch {
                        loadKodyarDatabase()
                        fetchSubscriptionPlans()
                        val token = getSessionToken()
                        if (token != null) {
                            loadCurrentUser(token)
                        }
                    }
                }
            }
            networkCallback = callback
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        } catch (e: Exception) {
            Log.e("AssistantViewModel", "Failed to register network callback.", e)
        }
    }

    // --- Global Manual App Refresh ---
    private val _isGlobalRefreshing = MutableStateFlow(false)
    val isGlobalRefreshing: StateFlow<Boolean> = _isGlobalRefreshing.asStateFlow()

    fun refreshAllAppData(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isGlobalRefreshing.value = true
            var success = false
            try {
                // 1. Refresh user session, VIP/Premium status, and profile info
                checkSavedSession()
                // 2. Refresh subscription plans
                fetchSubscriptionPlans()
                // 3. Refresh free usage counts
                loadFreeStatus()
                // 4. Refresh repair orders and part purchase histories
                loadRepairs()
                // 5. Refresh diagnostic data and spare parts
                loadKodyarDatabase()
                success = true
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Error in global refresh", e)
            } finally {
                _isGlobalRefreshing.value = false
                onComplete?.invoke(success)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("AssistantViewModel", "Failed to unregister network callback on clear.", e)
            }
        }
    }
}

class AssistantViewModelFactory(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssistantViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class AppUpdateNotification(
    val newErrors: List<com.example.data.model.KodyarErrorCode>,
    val newProblems: List<com.example.data.model.KodyarCommonProblem>,
    val newParts: List<com.example.data.model.KodyarSparePart>
)

data class AppUpdateInfo(
    val latestVersionCode: Int = com.example.BuildConfig.VERSION_CODE,
    val latestVersionName: String = com.example.BuildConfig.VERSION_NAME,
    val isForceUpdate: Boolean = false,
    val updateTitle: String = "بروزرسانی جدید کدیار۲۴",
    val updateDescription: String = "نسخه ${com.example.BuildConfig.VERSION_NAME} اپلیکیشن تخصصی کدیار۲۴ منتشر شد.\nبرای استفاده از امکانات جدید، لطفاً نسخه جدید را دریافت کنید.",
    val notes: List<String> = listOf(
        "ارتقا به نسخه جدید (${com.example.BuildConfig.VERSION_NAME})",
        "امکان بازیابی آنلاین رمز عبور با کد پیامک سریع",
        "بهبود سرعت بارگذاری و رفع مشکلات گزارش شده"
    )
)

