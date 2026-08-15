package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.KodyarSparePart

// Custom Theme Colors matching Codyar HTML
val CodyarNavy: Color @Composable get() = MaterialTheme.colorScheme.primary
val CodyarRed: Color @Composable get() = MaterialTheme.colorScheme.secondary
val CodyarBg: Color @Composable get() = MaterialTheme.colorScheme.background
val CodyarSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val CodyarOnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val CodyarBorder: Color @Composable get() = MaterialTheme.colorScheme.outline
val CodyarTextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val CodyarTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

fun normalizePersian(input: String?): String {
    if (input == null) return ""
    return input.trim()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ة", "ه")
        .replace("\\s+".toRegex(), " ")
}

fun extractModelFromText(text: String): String? {
    if (text.isBlank()) return null
    val norm = normalizePersian(text)
    val modelRegex = Regex("""مدل\s+([آ-یa-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)
    val match = modelRegex.find(norm)
    if (match != null) {
        val found = match.groupValues[1].trim()
        if (found.length >= 2 && found != "های" && found != "مختلف") {
            return found
        }
    }
    val commonModels = listOf(
        "روما", "اپتیما", "پرلا", "بیتا", "پاویا", "کالدا ونزیا", "ورونا", "پیرلا",
        "پارما", "پستیو", "پلی اتان", "کالدا", "M24", "L24", "CV424", "L28", "BN", "K24"
    )
    for (m in commonModels) {
        if (norm.contains(m, ignoreCase = true)) {
            return m
        }
    }
    return null
}

fun extractComponentKeyword(text: String): String? {
    if (text.isBlank()) return null
    val norm = normalizePersian(text).lowercase()
    val components = listOf(
        "مبدل", "پمپ", "فن", "برد", "پرشر", "فلوسویچ", "فلومتر",
        "سنسور", "شیر برقی", "شیر سه طرفه", "شیر گاز", "شیر پرکن", "شیر اطمینان", "شیر",
        "منبع انبساط", "ترموستات", "جرقه زن", "الکترود", "یون", "گیج", "مانومتر",
        "ترموکوپل", "تخلیه", "بلبرینگ", "کاسه نمد", "واشر", "اورینگ", "دیافراگم"
    )
    for (comp in components) {
        if (norm.contains(comp)) {
            return comp
        }
    }
    return null
}

fun buildSparePartQueryForError(
    category: String?,
    brand: String?,
    model: String?,
    title: String?,
    code: String?,
    description: String?,
    causes: Any?
): String {
    val queryParts = mutableListOf<String>()

    if (!category.isNullOrBlank()) {
        queryParts.add(category.trim())
    }

    if (!brand.isNullOrBlank()) {
        queryParts.add(brand.trim())
    }

    val extractedModel = when {
        !model.isNullOrBlank() -> model.trim()
        !title.isNullOrBlank() -> extractModelFromText(title)
        !description.isNullOrBlank() -> extractModelFromText(description)
        else -> null
    }
    if (!extractedModel.isNullOrBlank() && !queryParts.contains(extractedModel)) {
        queryParts.add(extractedModel)
    }

    val fullText = listOfNotNull(title, description, causes?.toString(), code).joinToString(" ")
    val component = extractComponentKeyword(fullText)
    if (!component.isNullOrBlank() && !queryParts.contains(component)) {
        queryParts.add(component)
    }

    return if (queryParts.isNotEmpty()) {
        queryParts.joinToString(" ")
    } else {
        code ?: title ?: ""
    }
}

fun filterSparePartsByQuery(parts: List<KodyarSparePart>, query: String): List<KodyarSparePart> {
    if (query.isBlank()) return parts

    val normQuery = normalizePersian(query).lowercase().trim()
    val knownBrands = setOf(
        "بوتان", "ایران رادیاتور", "ایساتیز", "بوش", "تاچی", "لورچ", "آریستون", "ورونا",
        "گلدیران", "الجی", "سامسونگ", "اسنوا", "دوو", "امرسان", "پارس", "هیمالیا", "جنرال",
        "التروستیل", "بیکاس", "فرولی", "مکس لیت", "باپسی", "پایکان", "والرو", "ایمرگاس",
        "آگ", "باکسی", "ایکس ویژن", "هایسنس", "تکنوگاز", "سپهر الکتریک", "آبسال", "دونار",
        "میدیا", "بکو", "آدمیرال", "تپسی", "کنوود", "پارس خزر", "گرنیه"
    )

    val knownCategories = setOf(
        "پکیج", "آبگرمکن", "لباسشویی", "ظرفشویی", "کولر گازی", "یخچال", "ماکروفر", "جاروبرقی"
    )

    val knownComponents = setOf(
        "مبدل", "پمپ", "فن", "برد", "پرشر", "فلوسویچ", "فلومتر", "سنسور", "ان تی سی", "ntc",
        "شیر برقی", "شیر سه طرفه", "شیر گاز", "شیر پرکن", "شیر اطمینان", "شیر",
        "منبع انبساط", "ترموستات", "جرقه زن", "الکترود", "یون", "گیج", "مانومتر",
        "ترموکوپل", "تخلیه", "بلبرینگ", "کاسه نمد", "واشر", "اورینگ", "دیافراگم"
    )

    val matchedBrandsInQuery = knownBrands.filter { normQuery.contains(it) }
    val matchedCategoriesInQuery = knownCategories.filter { normQuery.contains(it) }
    val matchedComponentsInQuery = knownComponents.filter { normQuery.contains(it) }

    val stopWords = setOf("مدل", "دستگاه", "کد", "خطا", "ارور", "و", "یا", "به", "با", "در", "برای")
    val queryTokens = normQuery.split(Regex("""[\s_()\-\[\]]+"""))
        .filter { it.length >= 2 && !stopWords.contains(it) }

    val modelTokens = queryTokens.filter { token ->
        !matchedBrandsInQuery.contains(token) &&
        !matchedCategoriesInQuery.contains(token) &&
        !matchedComponentsInQuery.contains(token)
    }

    val scoredParts = mutableListOf<Pair<KodyarSparePart, Int>>()

    for (part in parts) {
        val partNameNorm = normalizePersian(part.name ?: "").lowercase()
        val partBrandNorm = normalizePersian(part.brand ?: "").lowercase()
        val combinedNorm = "$partNameNorm $partBrandNorm"

        // 1. Strict Brand Filtering
        if (matchedBrandsInQuery.isNotEmpty()) {
            val partHasMatchedBrand = matchedBrandsInQuery.any { b ->
                partBrandNorm.contains(b) || partNameNorm.contains(b)
            }
            val otherBrandsInPart = knownBrands.filter { b ->
                !matchedBrandsInQuery.contains(b) && (partBrandNorm.contains(b) || partNameNorm.contains(b))
            }

            if (!partHasMatchedBrand && otherBrandsInPart.isNotEmpty()) {
                continue
            }
        }

        // 2. Strict Category Filtering
        if (matchedCategoriesInQuery.isNotEmpty()) {
            val otherCategoriesInPart = knownCategories.filter { c ->
                !matchedCategoriesInQuery.contains(c) && combinedNorm.contains(c)
            }
            if (otherCategoriesInPart.isNotEmpty() && !matchedCategoriesInQuery.any { combinedNorm.contains(it) }) {
                continue
            }
        }

        // 3. Score Calculation
        var score = 0

        if (combinedNorm.contains(normQuery)) {
            score += 200
        }

        for (m in modelTokens) {
            if (partNameNorm.contains(m)) {
                score += 100
            }
        }

        for (comp in matchedComponentsInQuery) {
            if (partNameNorm.contains(comp)) {
                score += 60
            }
        }

        for (b in matchedBrandsInQuery) {
            if (partBrandNorm.contains(b) || partNameNorm.contains(b)) {
                score += 40
            }
        }

        for (t in queryTokens) {
            if (combinedNorm.contains(t)) {
                score += 10
            }
        }

        if (score > 0 || (matchedBrandsInQuery.isEmpty() && matchedComponentsInQuery.isEmpty() && modelTokens.isEmpty())) {
            scoredParts.add(part to score)
        }
    }

    scoredParts.sortByDescending { it.second }
    val result = scoredParts.map { it.first }

    return if (result.isNotEmpty()) {
        result
    } else {
        if (matchedBrandsInQuery.isNotEmpty()) {
            parts.filter { part ->
                val pBrand = normalizePersian(part.brand ?: "").lowercase()
                val pName = normalizePersian(part.name ?: "").lowercase()
                matchedBrandsInQuery.any { b -> pBrand.contains(b) || pName.contains(b) }
            }
        } else {
            emptyList()
        }
    }
}

val voiceWordMapping = mapOf(
    "صفر" to "0",
    "یک" to "1",
    "دو" to "2",
    "سه" to "3",
    "چهار" to "4",
    "پنج" to "5",
    "شش" to "6",
    "شیش" to "6",
    "هفت" to "7",
    "هشت" to "8",
    "نه" to "9",
    "ده" to "10",
    "ای" to "e",
    "اِی" to "e",
    "یی" to "e",
    "اف" to "f",
    "اِف" to "f",
    "پی" to "p",
    "دی" to "d",
    "سی" to "c",
    "اچ" to "h",
    "اِچ" to "h",
    "یو" to "u",
    "ال" to "l",
    "ار" to "r",
    "او" to "o",
    "تی" to "t",
    "آی" to "i",
    "س" to "c",
    "ف" to "f",
    "پ" to "p"
)

fun normalizeVoiceSearchText(text: String): String {
    if (text.isEmpty()) return ""
    
    var normalized = text
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
    
    val noiseWords = listOf("ارور", "خطای", "خطا", "کد", "کدهای")
    val words = normalized.split(Regex("\\s+"))
    val resultWords = mutableListOf<String>()
    
    for (word in words) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty() || noiseWords.contains(cleanWord)) {
            continue
        }
        
        val mapped = voiceWordMapping[cleanWord.lowercase()]
        if (mapped != null) {
            resultWords.add(mapped)
        } else {
            resultWords.add(cleanWord)
        }
    }
    
    val sb = java.lang.StringBuilder()
    for (i in resultWords.indices) {
        val current = resultWords[i]
        sb.append(current)
        if (i < resultWords.size - 1) {
            val next = resultWords[i + 1]
            val currentIsShort = current.length == 1 || (current.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') })
            val nextIsShort = next.length == 1 || (next.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') })
            if (!(currentIsShort && nextIsShort)) {
                sb.append(" ")
            }
        }
    }
    return sb.toString()
}

fun normalizePersianText(input: String): String {
    return input.lowercase()
        .replace("ك", "ک")
        .replace("ي", "ی")
        .replace("‌", "")
        .replace(" ", "")
        .replace("-", "")
        .replace("آ", "ا")
}

fun formatToman(price: Double): String {
    return String.format("%,.0f", price)
}

fun convertGregorianToJalali(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    val trimmed = dateString.trim()
    if (trimmed.startsWith("13") || trimmed.startsWith("14") || trimmed.startsWith("۱۴") || trimmed.startsWith("۱۳")) {
        return trimmed
    }
    if (!trimmed.any { it.isDigit() }) {
        return trimmed
    }
    try {
        val cleanDate = trimmed.substringBefore("T").substringBefore(" ").trim()
        val parts = if (cleanDate.contains("-")) cleanDate.split("-") else cleanDate.split("/")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: return dateString
            val month = parts[1].toIntOrNull() ?: return dateString
            val day = parts[2].toIntOrNull() ?: return dateString
            
            val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 335)
            var gy = year - 1600
            var gm = month - 1
            var gd = day - 1

            var gDayNo = 365 * gy + (gy + 4) / 4 - (gy + 100) / 100 + (gy + 400) / 400
            gDayNo += gDaysInMonth[gm]
            if (gm > 1 && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))) {
                gDayNo++
            }
            gDayNo += gd

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            var jd = 0
            for (i in 0..11) {
                val monthLength = if (i < 6) 31 else if (i < 11) 30 else 29
                if (jDayNo < monthLength) {
                    jm = i + 1
                    jd = jDayNo + 1
                    break
                }
                jDayNo -= monthLength
            }
            
            val persianDigits = listOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
            val rawPersianDate = "$jy/${String.format("%02d", jm)}/${String.format("%02d", jd)}"
            return rawPersianDate.map { char ->
                if (char.isDigit()) persianDigits[char.toString().toInt()] else char
            }.joinToString("")
        }
    } catch (e: Exception) {
        // ignore
    }
    return dateString
}

fun getCurrentJalaliDate(): String {
    val calendar = java.util.Calendar.getInstance()
    val year = calendar.get(java.util.Calendar.YEAR)
    val month = calendar.get(java.util.Calendar.MONTH) + 1
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val gDate = "$year-${String.format("%02d", month)}-${String.format("%02d", day)}"
    return convertGregorianToJalali(gDate)
}

@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
fun FilterDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(expanded) {
        if (!expanded) {
            filterText = ""
        }
    }

    val filteredOptions = remember(options, filterText) {
        if (filterText.isEmpty()) {
            options
        } else {
            val normFilter = normalizePersian(filterText)
            options.filter {
                it == "همه" || normalizePersian(it).contains(normFilter, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CodyarTextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedValue,
                    fontSize = 12.sp,
                    color = if (selectedValue == "همه") Color(0xFF64748B) else CodyarTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = CodyarTextSecondary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 280.dp)
                    .background(CodyarSurface)
            ) {
                if (options.size > 5) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("جستجو...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (filterText.isNotEmpty()) {
                                IconButton(onClick = { filterText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CodyarNavy,
                            unfocusedBorderColor = Color(0xFFDDE1E7),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        )
                    )
                }

                if (filteredOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "موردی یافت نشد",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {}
                    )
                } else {
                    filteredOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )
                            },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

fun openBazaarUpdate(context: Context, appPackageName: String = "ir.novincol.com") {
    try {
        val bazaarIntent = Intent(Intent.ACTION_VIEW, Uri.parse("bazaar://details?id=$appPackageName")).apply {
            setPackage("com.farsitel.bazaar")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(bazaarIntent)
    } catch (e: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/app/$appPackageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            val apkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kodyar24.ir/download/app.apk")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(apkIntent)
        }
    }
}

fun compressAndEncodeUriToBase64(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val maxDim = 1024
        var scale = 1
        while (options.outWidth / scale > maxDim || options.outHeight / scale > maxDim) {
            scale *= 2
        }

        val options2 = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = scale
        }
        val inputStream2 = context.contentResolver.openInputStream(uri) ?: return ""
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, options2)
        inputStream2.close()

        if (bitmap != null) {
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()
            bitmap.recycle()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}
