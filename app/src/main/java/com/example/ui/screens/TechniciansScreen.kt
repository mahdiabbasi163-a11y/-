package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.KodyarTechnician
import com.example.data.model.KodyarUser
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TechniciansScreen(
    viewModel: AssistantViewModel,
    liveTechs: StateFlow<List<KodyarTechnician>>,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit
) {
    val context = LocalContext.current
    val techsList by liveTechs.collectAsState()
    val userCity = if (!currentUser?.city.isNullOrBlank()) currentUser.city else null
    val liveCitiesRaw by viewModel.liveCities.collectAsState()
    
    // Dynamically insert user's city if it exists and is not in the list
    val liveCities = remember(liveCitiesRaw, userCity) {
        if (userCity != null && !liveCitiesRaw.any { normalizePersian(it).equals(normalizePersian(userCity), ignoreCase = true) }) {
            val list = liveCitiesRaw.toMutableList()
            if (list.contains("همه")) {
                list.add(1, userCity)
            } else {
                list.add(0, userCity)
            }
            list
        } else {
            liveCitiesRaw
        }
    }

    var selectedCityFilter by remember { mutableStateOf("همه") }
    var sortBy by remember { mutableStateOf("top_rated") }
    var selectedTechForRepair by remember { mutableStateOf<KodyarTechnician?>(null) }

    var showChangeCityDialog by remember { mutableStateOf(false) }
    var newCityInput by remember { mutableStateOf("") }
    var changeCityExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshTechnicians()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "تکنسین‌های تایید شده (${techsList.size} نفر)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CodyarNavy
            )
            if (techsList.isNotEmpty()) {
                Surface(
                    color = Color(0xFFE0F2FE),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "🟢 دیتابیس آنلاین",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0369A1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Persistent city-based information banner
        if (showChangeCityDialog) {
            val liveCitiesList by viewModel.liveCities.collectAsState()
            val filteredCities = remember(newCityInput, liveCitiesList) {
                val cleanQuery = newCityInput.trim().lowercase()
                if (cleanQuery.isEmpty()) {
                    liveCitiesList.filter { it != "همه" }
                } else {
                    liveCitiesList.filter { it != "همه" && it.lowercase().contains(cleanQuery) }
                }
            }

            AlertDialog(
                onDismissRequest = { showChangeCityDialog = false },
                title = { Text("تغییر شهر سکونت", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarNavy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("لطفاً نام شهر یا محله خود را از لیست انتخاب یا جستجو کنید:", fontSize = 12.sp, color = Color.Gray)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newCityInput,
                                onValueChange = { 
                                    newCityInput = it
                                    changeCityExpanded = true
                                },
                                placeholder = { Text("مثلا: اراک") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { changeCityExpanded = !changeCityExpanded }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "نمایش لیست"
                                        )
                                    }
                                }
                            )

                            if (changeCityExpanded && filteredCities.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 150.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = CodyarSurface)
                                ) {
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        filteredCities.forEach { cityItem ->
                                            TextButton(
                                                onClick = {
                                                    newCityInput = cityItem
                                                    changeCityExpanded = false
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = cityItem,
                                                    fontSize = 12.sp,
                                                    color = Color.Black,
                                                    textAlign = TextAlign.Right,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            HorizontalDivider(color = Color(0xFFF1F5F9))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newCityInput.isNotBlank()) {
                                val updatedUser = currentUser?.copy(city = newCityInput.trim())
                                if (updatedUser != null) {
                                    viewModel.updateUserCityLocally(updatedUser)
                                }
                                showChangeCityDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                    ) {
                        Text("ثبت", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeCityDialog = false }) {
                        Text("انصراف", color = Color.Gray)
                    }
                }
            )
        }

        if (userCity != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 نمایش تکنسین‌های فعال در شهر شما: $userCity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF1E40AF),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { 
                            newCityInput = userCity
                            showChangeCityDialog = true 
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("تغییر شهر", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 برای مشاهده تکنسین‌های فعال در محدوده خود، شهر خود را تعیین کنید",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { 
                            newCityInput = ""
                            showChangeCityDialog = true 
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("تعیین شهر", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val targetCity = userCity ?: currentUser?.city ?: "اراک"

        val listAvatars = remember {
            listOf(
                "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&h=150&fit=crop",
                "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150&h=150&fit=crop"
            )
        }

        // Sorting Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مرتب‌سازی:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CodyarTextSecondary
            )
            
            val sortOptions = listOf(
                "top_rated" to "⭐ برترین‌ها (امتیاز بالا)",
                "most_orders" to "🛠️ پرکارترین‌ها",
                "all" to "👤 همه"
            )
            
            sortOptions.forEach { (optionKey, optionLabel) ->
                val isActive = sortBy == optionKey
                Box(
                    modifier = Modifier
                        .background(
                            if (isActive) CodyarNavy.copy(alpha = 0.12f) else Color(0xFFF3F4F6),
                            RoundedCornerShape(30.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) CodyarNavy else Color.Transparent,
                            shape = RoundedCornerShape(30.dp)
                        )
                        .clickable { sortBy = optionKey }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = optionLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) CodyarNavy else CodyarTextPrimary
                    )
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val baseFiltered = techsList.filter { tech ->
                if (selectedCityFilter == "همه") {
                    true
                } else {
                    val targetCity = selectedCityFilter.ifBlank { userCity ?: "" }
                    if (targetCity.isBlank() || targetCity == "همه") true
                    else viewModel.areCitiesCompatible(tech.resolvedCity, targetCity)
                }
            }

            val filtered = when (sortBy) {
                "top_rated" -> baseFiltered.sortedWith(
                    compareByDescending<KodyarTechnician> { it.rating ?: 5.0 }
                        .thenByDescending { it.satisfactionRate ?: 100 }
                        .thenByDescending { it.completedOrders ?: 0 }
                )
                "most_orders" -> baseFiltered.sortedByDescending { it.completedOrders ?: 0 }
                else -> baseFiltered
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📍", fontSize = 28.sp)
                            Text(
                                "تکنسینی در شهر $targetCity ثبت نشده است",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "در حال حاضر تکنسین فعالی برای شهر مورد نظر ثبت نشده است. در صورت نیاز به هماهنگی تکنسین یا ثبت نهایی درخواست تعمیرات، لطفاً با پشتیبانی کدیار۲۴ ارتباط برقرار کنید.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                items(filtered) { tech ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Avatar on the side
                            val avatarUrl = tech.resolvedAvatarUrl
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, CodyarNavy, CircleShape)
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUrl != null) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = tech.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = tech.name,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tech.name ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFEAFAF1), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text("✓ تایید شده", color = Color(0xFF1E8449), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        "📍 ${tech.city ?: "نامشخص"}",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text("•", fontSize = 11.sp, color = Color.LightGray)
                                    Text(
                                        "🛠️ ${tech.completedOrders ?: 0} سرویس",
                                        fontSize = 11.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text("•", fontSize = 11.sp, color = Color.LightGray)
                                    
                                    // Star Rating
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB000),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            String.format(java.util.Locale.US, "%.1f", tech.rating ?: 5.0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CodyarTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            "(${tech.satisfactionRate ?: 100}% رضایت)",
                                            fontSize = 10.sp,
                                            color = Color(0xFF1E8449),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (!tech.bio.isNullOrBlank()) {
                                    Text(
                                        tech.bio,
                                        fontSize = 11.sp,
                                        color = CodyarTextPrimary,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(bottom = 7.dp)
                                    )
                                }

                                // Categories
                                if (tech.resolvedCategories.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        tech.resolvedCategories.forEach { cat ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE8EAF0), RoundedCornerShape(5.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(cat, color = CodyarTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (currentUser == null) {
                                            onShowAuth()
                                            return@Button
                                        }
                                        selectedTechForRepair = tech
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("اعزام تکنسین", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("تکنسینی ثبت نشده است", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("به زودی تکنسین‌های تایید شده اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Bottom CTA for techs
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("تکنسین هستید؟", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            "با همکاران ما در وب‌سایت کدیار۲۴ تماس بگیرید و پس از تایید مدارک سفارش کار دریافت کنید.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("ثبت‌نام تکنسین در سایت", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG FOR REQUESTING DISPATCH ---
    selectedTechForRepair?.let { tech ->
        var deviceBrand by remember { mutableStateOf("") }
        var problemDesc by remember { mutableStateOf("") }
        var preferredDate by remember { mutableStateOf("") }
        var contactPhone by remember(currentUser) { mutableStateOf(currentUser?.phone ?: "") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) selectedTechForRepair = null },
            title = {
                Text(
                    text = "درخواست اعزام تکنسین (${tech.name ?: "کارشناس"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CodyarNavy,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "جهت هماهنگی دقیق مراجعه تکنسین، مشخصات زیر را تکمیل کنید:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Device and Brand input
                    OutlinedTextField(
                        value = deviceBrand,
                        onValueChange = { deviceBrand = it },
                        label = { Text("نوع دستگاه و برند", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: ماشین لباسشویی سامسونگ", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Problem description input
                    OutlinedTextField(
                        value = problemDesc,
                        onValueChange = { problemDesc = it },
                        label = { Text("شرح مختصر مشکل یا خرابی", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: روشن نمی‌شود یا صدای غیرعادی می‌دهد", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Preferred date and time input
                    OutlinedTextField(
                        value = preferredDate,
                        onValueChange = { preferredDate = it },
                        label = { Text("تاریخ و ساعت پیشنهادی جهت مراجعه", fontSize = 11.sp) },
                        placeholder = { Text("مثلاً: فردا پنجشنبه ساعت ۱۵ الی ۱۸", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )

                    // Contact phone input
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("شماره همراه جهت هماهنگی", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deviceBrand.isBlank() || problemDesc.isBlank() || preferredDate.isBlank() || contactPhone.isBlank()) {
                            Toast.makeText(context, "لطفاً تمامی فیلدها را تکمیل کنید", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        val repairCity = currentUser?.city?.takeIf { it.isNotBlank() }
                            ?: tech.city?.takeIf { it.isNotBlank() }
                            ?: "تهران"

                        val formattedDesc = "دستگاه و برند: $deviceBrand\n" +
                                "شرح خرابی: $problemDesc\n" +
                                "زمان پیشنهادی مراجعه کارشناس: $preferredDate\n" +
                                "تلفن تماس هماهنگی: $contactPhone"

                        viewModel.submitRepairRequest(
                            techId = tech.id ?: "",
                            description = formattedDesc,
                            city = repairCity
                        ) { success, err ->
                            isSubmitting = false
                            if (success) {
                                selectedTechForRepair = null
                                Toast.makeText(
                                    context,
                                    "✅ درخواست اعزام با موفقیت ثبت شد. تکنسین جهت هماهنگی با شما تماس می‌گیرد.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, err ?: "خطا در ثبت درخواست", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("ثبت نهایی درخواست", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedTechForRepair = null },
                    enabled = !isSubmitting
                ) {
                    Text("انصراف", color = Color.Gray, fontSize = 12.sp)
                }
            }
        )
    }
}
