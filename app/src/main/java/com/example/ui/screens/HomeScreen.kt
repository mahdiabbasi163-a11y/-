package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.KodyarErrorCode
import com.example.data.model.KodyarTechnician
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: AssistantViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    onShowPlans: () -> Unit,
    onOpenErrorCode: (KodyarErrorCode) -> Unit
) {
    val scrollState = rememberScrollState()
    var homeSearchQuery by remember { mutableStateOf("") }
    val liveErrorCodes by viewModel.liveErrorCodes.collectAsState()
    val liveTechs by viewModel.liveTechnicians.collectAsState()
    val liveParts by viewModel.liveSpareParts.collectAsState()
    val rawCategories by viewModel.liveCategories.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val displayCategories = remember(rawCategories) {
        val filtered = rawCategories.filter { it != "همه" && it.isNotBlank() }
        if (filtered.isEmpty()) {
            listOf("کولر گازی", "یخچال و فریزر", "ماشین لباسشویی", "ماشین ظرفشویی")
        } else {
            filtered
        }
    }

    val displayTechs = remember(liveTechs, currentUser) {
        val filteredLive = liveTechs.filter { tech ->
            val isSelf = currentUser != null && (
                (!tech.id.isNullOrBlank() && tech.id == currentUser?.id) ||
                (!tech.name.isNullOrBlank() && currentUser?.full_name != null && tech.name == currentUser?.full_name)
            )
            !isSelf
        }
        filteredLive.sortedWith(
            compareByDescending<KodyarTechnician> { it.completedOrders ?: 0 }
                .thenByDescending { it.rating ?: 0.0 }
                .thenByDescending { it.satisfactionRate ?: 0 }
        ).take(4)
    }

    val avatars = remember {
        listOf(
            "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150&h=150&fit=crop",
            "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150&h=150&fit=crop"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero search banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodyarNavy)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مرجع عیب‌یابی لوازم خانگی",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "از کد خطا تا راه حل، هوشمند و سریع",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 28.sp
                        )
                    }
                    
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val shareMessage = if (currentUser?.role == "technician") {
                                "سلام همکار گرامی!\nبرنامه تخصصی کدیار۲۴ (مرجع عیب‌یابی هوشمند و کدهای خطای لوازم خانگی مخصوص تکنسین‌ها و تعمیرکاران) رو دانلود کن و همیشه همراهت داشته باش:\nhttps://kodyar24.ir/download/app.apk"
                            } else {
                                "سلام!\nاگر برای عیب‌یابی لوازم خانگی خونت به یک دستیار هوشمند و سریع نیاز داری، اپلیکیشن کدیار۲۴ رو از لینک زیر دانلود کن:\nhttps://kodyar24.ir/download/app.apk"
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "دانلود اپلیکیشن کدیار۲۴")
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری کدیار۲۴ از طریق:"))
                        },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "اشتراک‌گذاری برنامه",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Search field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodyarSurface, RoundedCornerShape(12.dp))
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو",
                        tint = CodyarTextSecondary,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 8.dp)
                            .size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (homeSearchQuery.isEmpty()) {
                            Text(
                                "مثلاً: E1، F2، بوتان...",
                                color = Color(0xFFB0B8C4),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = homeSearchQuery,
                            onValueChange = { homeSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                color = CodyarTextPrimary,
                                textAlign = TextAlign.Start
                            )
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.updateSearchFilters(homeSearchQuery, "همه", "همه")
                            onNavigateToSearch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("جستجو", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Stats card (Floating offset over hero)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = (-20).dp),
            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFEAECEF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔴", fontSize = 17.sp)
                    Text(
                        text = "${liveErrorCodes.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Text("کد خطا", fontSize = 10.sp, color = CodyarTextSecondary)
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAECEF))
                        .width(1.dp)
                        .height(35.dp)
                )

                // Stat 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔧", fontSize = 17.sp)
                    Text(
                        text = "${liveTechs.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Text("تکنسین", fontSize = 10.sp, color = CodyarTextSecondary)
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAECEF))
                        .width(1.dp)
                        .height(35.dp)
                )

                // Stat 3
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📦", fontSize = 17.sp)
                    Text(
                        text = "${liveParts.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CodyarTextPrimary
                    )
                    Text("قطعه", fontSize = 10.sp, color = CodyarTextSecondary)
                }
            }
        }

        // Quick action grid (4 actions)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            val quickActions = listOf(
                QuadAction(
                    icon = Icons.Default.Search,
                    tint = CodyarRed,
                    label = "جستجوی کد خطا",
                    sub = "عیب‌یابی سریع",
                    action = onNavigateToSearch
                ),
                QuadAction(
                    icon = Icons.Default.Build,
                    tint = CodyarNavy,
                    label = "اعزام تکنسین",
                    sub = "در شهر شما",
                    action = onNavigateToTechnicians
                ),
                QuadAction(
                    icon = Icons.Default.ShoppingCart,
                    tint = Color(0xFF1E8449),
                    label = "فروشگاه قطعات",
                    sub = "قطعات اصل",
                    action = {
                        viewModel.clearStoreSearchQuery()
                        onNavigateToStore()
                    }
                ),
                QuadAction(
                    icon = Icons.Default.Star,
                    tint = Color(0xFFC9A227),
                    label = "اشتراک ویژه",
                    sub = "از ۱۵۰,۰۰۰ تومان",
                    action = onShowPlans
                )
            )

            quickActions.forEach { item ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { item.action() }
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(0.dp, Color.Transparent)
                            )
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(item.tint.copy(alpha = 0.1f), CircleShape)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = item.tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary
                            )
                            Text(
                                text = item.sub,
                                fontSize = 10.sp,
                                color = CodyarTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- SECTION: Colleagues & Top Technicians Recommendations ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFEAECEF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with a smiling technician icon
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🔧", fontSize = 16.sp)
                        Text(
                            text = "توصیه‌کنندگان و همکاران برتر",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CodyarTextPrimary
                        )
                    }
                    Text(
                        text = "بانک ارور",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CodyarRed
                    )
                }

                // Horizontal list of circular avatars or empty indicator
                if (displayTechs.isEmpty()) {
                    Text(
                        text = "تکنسینی در دیتابیس ثبت نشده است",
                        fontSize = 12.sp,
                        color = CodyarTextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayTechs.forEachIndexed { idx, tech ->
                            val avatarUrl = if (!tech.image.isNullOrBlank()) tech.image else if (!tech.imageUrl.isNullOrBlank()) tech.imageUrl else null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (idx % 2 == 0) CodyarRed else CodyarNavy, CircleShape)
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
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = tech.name ?: "همکار کدیار",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CodyarTextPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SECTION: Category recommendations matching user screenshot ---
        Text(
            text = "دسته‌بندی‌های پیشنهادی همکاران",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CodyarTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        val categoryColors = listOf(
            Color(0xFF0EA5E9), // Sky Blue
            Color(0xFF8B5CF6), // Purple
            Color(0xFFF97316), // Orange
            Color(0xFF10B981), // Emerald Green
            Color(0xFFEF4444), // Rose Red
            Color(0xFF06B6D4)  // Cyan
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            displayCategories.forEachIndexed { index, cat ->
                val cardColor = categoryColors[index % categoryColors.size]
                
                val recommendingTech = remember(liveTechs, cat) {
                    val matchingTechs = liveTechs.filter { it.categories?.any { specialty -> specialty.contains(cat, ignoreCase = true) || cat.contains(specialty, ignoreCase = true) } == true }
                    if (matchingTechs.isNotEmpty()) {
                        matchingTechs.maxByOrNull { tech ->
                            val orders = tech.completedOrders ?: 0
                            val rating = tech.rating ?: 0.0
                            val satisfaction = tech.satisfactionRate ?: 0
                            (orders * 100) + (rating * 50).toInt() + satisfaction
                        }
                    } else {
                        null
                    }
                }
                
                val techName = recommendingTech?.name ?: "تکنسین کدیار"
                val avatarUrl = if (recommendingTech != null && !recommendingTech.image.isNullOrBlank()) recommendingTech.image else if (recommendingTech != null && !recommendingTech.imageUrl.isNullOrBlank()) recommendingTech.imageUrl else null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            viewModel.updateSearchFilters("", "همه", cat)
                            onNavigateToSearch()
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Technician image on left
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = techName,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(bottomStart = 18.dp, topStart = 18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = techName,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                        // Text details on right
                        Column(
                            modifier = Modifier
                                .weight(1.8f)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "بانک ارور $cat",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "+ $techName پیشنهاد می‌کند",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION: Engaging Share App Banner ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left/Start side: Nice share visual indicator
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFEFF6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📢", fontSize = 24.sp)
                }

                // Center: Text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "معرفی کدیار۲۴ به همکاران",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CodyarTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "با ارسال لینک برنامه برای سایر همکاران و تکنسین‌ها، به آنها در حل سریع کدهای خطا کمک کنید.",
                        fontSize = 11.sp,
                        color = CodyarTextSecondary,
                        lineHeight = 16.sp
                    )
                }

                // Right/End side: Styled Button
                val context = LocalContext.current
                Button(
                    onClick = {
                        val shareMessage = if (currentUser?.role == "technician") {
                            "سلام همکار گرامی!\nبرنامه تخصصی کدیار۲۴ (مرجع عیب‌یابی هوشمند و کدهای خطای لوازم خانگی مخصوص تکنسین‌ها و تعمیرکاران) رو دانلود کن و همیشه همراهت داشته باش:\nhttps://kodyar24.ir/download/app.apk"
                        } else {
                            "سلام!\nاگر برای عیب‌یابی لوازم خانگی خونت به یک دستیار هوشمند و سریع نیاز داری، اپلیکیشن کدیار۲۴ رو از لینک زیر دانلود کن:\nhttps://kodyar24.ir/download/app.apk"
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "دانلود اپلیکیشن کدیار۲۴")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری کدیار۲۴ از طریق:"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("ارسال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // High frequency errors header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CodyarRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "پرتکرارترین خطاها",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CodyarTextPrimary
                )
            }

            TextButton(onClick = onNavigateToSearch) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("همه", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CodyarRed)
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = CodyarRed,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // List of top 5 errors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            liveErrorCodes.take(5).forEach { err ->
                val severityLevel = err.hazardLevel ?: "medium"
                val (color, bg, label) = when (severityLevel) {
                    "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "خطرناک")
                    "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "کم‌خطر")
                    else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenErrorCode(err) },
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = err.code ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = err.title ?: err.code ?: "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                fontSize = 11.sp,
                                color = CodyarTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(bg, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

data class QuadAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val label: String,
    val sub: String,
    val action: () -> Unit
)
