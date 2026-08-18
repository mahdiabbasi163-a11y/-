package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KodyarRepairOrder
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun OrdersScreen(
    viewModel: AssistantViewModel,
    repairOrders: List<KodyarRepairOrder>,
    isRepairsLoading: Boolean,
    onBack: () -> Unit,
    onNavigateToTechs: () -> Unit
) {
    val partPurchases by viewModel.partPurchases.collectAsState()
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Repair Orders, 1: Part Purchases

    val statusMap = mapOf(
        "pending" to Triple("در انتظار", Color(0xFFD68910), Color(0xFFFEF9E7)),
        "accepted" to Triple("تایید شده", Color(0xFF1E8449), Color(0xFFEAFAF1)),
        "ongoing" to Triple("در حال انجام", Color(0xFF2563EB), Color(0xFFEFF6FF)),
        "completed" to Triple("تکمیل شده", CodyarTextPrimary, Color(0xFFF0F2F5)),
        "rejected" to Triple("لغو شده", Color(0xFFC0392B), Color(0xFFFDF0EE))
    )

    LaunchedEffect(Unit) {
        viewModel.loadRepairs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = CodyarSurface,
                contentColor = CodyarTextPrimary
            ),
            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = CodyarSurface,
            contentColor = CodyarNavy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("درخواست‌های تعمیر", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("سفارش‌های قطعات", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedSubTab == 0) {
            // Repair Orders
            val currentUser by viewModel.currentUser.collectAsState()
            val context = LocalContext.current

            if (currentUser?.role == "technician" && currentUser?.isApprovedUser != true) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCE8)),
                    border = BorderStroke(1.dp, Color(0xFFFEF08A)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📋", fontSize = 24.sp)
                            Column {
                                Text(
                                    "وضعیت حساب تکنسین: در انتظار بررسی و تایید مدیریت ⏳",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF854D0E)
                                )
                                Text(
                                    "شهر فعالیت: ${currentUser?.city ?: "ثبت نشده"}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA16207)
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFFEF08A))
                        Text(
                            "تکنسین گرامی (${currentUser?.full_name ?: ""})! مدارک سه‌گانه شما (کارت ملی، گواهی فنی، جواز کسب) ثبت شده و برای مدیریت ارسال گردیده است. پس از ممیزی و تایید مدیریت، سفارش‌های درخواست تعمیر مشتریان همشهری در شهر ${currentUser?.city ?: ""} برای شما فعال خواهد شد.",
                            fontSize = 12.sp,
                            color = Color(0xFF713F12),
                            lineHeight = 18.sp
                        )
                        Text(
                            "💡 توجه: کلیه امکانات عمومی برنامه شامل کدهای خطا، مشکلات متداول، خرید قطعات یدکی و تهیه اشتراک، برای شما مانند سایر کاربران فعال و قابل استفاده است.",
                            fontSize = 11.sp,
                            color = Color(0xFF854D0E),
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.checkTechnicianApprovalStatus { _, message ->
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCA8A04))
                            ) {
                                Text("🔄 استعلام وضعیت تایید از سرور مدیریت", fontSize = 11.sp, color = Color(0xFF854D0E), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (isRepairsLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CodyarNavy)
                }
            } else if (repairOrders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🔧", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("سفارشی ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CodyarTextPrimary)
                    Text("جهت ثبت درخواست با تکنسین تماس حاصل فرمایید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp))
                    Button(
                        onClick = onNavigateToTechs,
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                    ) {
                        Text("لیست تکنسین‌ها")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repairOrders.size) { i ->
                        val o = repairOrders[i]
                        val sKey = o.status ?: "pending"
                        val (label, textCol, bgCol) = statusMap[sKey] ?: Triple("در انتظار", Color(0xFFD68910), Color(0xFFFEF9E7))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "درخواست تعمیر #${i + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!o.description.isNullOrBlank()) {
                                    Text(
                                        text = o.description,
                                        fontSize = 13.sp,
                                        color = CodyarTextPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .background(Color(0xFFF7F8FA), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    )
                                }

                                if (!o.city.isNullOrBlank()) {
                                    Text(
                                        text = "📍 شهر: ${o.city}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                if (o.resolvedCategory.isNotBlank() || o.resolvedBrand.isNotBlank()) {
                                    val catBrand = listOf(o.resolvedCategory, o.resolvedBrand).filter { it.isNotBlank() }.joinToString(" - ")
                                    Text(
                                        text = "🔧 دستگاه: $catBrand",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                if (!o.technician_name.isNullOrBlank()) {
                                    Text(
                                        text = "👨‍🔧 تکنسین: ${o.technician_name}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (o.resolvedDate.isNotBlank()) {
                                    Text(
                                        text = "📅 تاریخ ثبت: ${o.resolvedDate}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9AA3AF),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Part Purchases
            if (partPurchases.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📦", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هیچ سفارش قطعه‌ای ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CodyarTextPrimary)
                    Text("با مراجعه به فروشگاه می‌توانید قطعه مورد نظر خود را سفارش دهید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(partPurchases.size) { i ->
                        val purchase = partPurchases[i]

                        // Status styling: pending -> در انتظار تایید پرداخت, approved -> تایید شده, sent -> ارسال به پست, delivered -> تحویل شده
                        val rawStatus = (purchase.status ?: "").lowercase().trim()
                        val (statusText, textCol, bgCol) = when {
                            rawStatus in listOf("approved", "accepted", "processing", "تایید شد", "تایید شده", "در حال آماده سازی", "در حال آماده‌سازی") ->
                                Triple("تایید شده (در حال آماده‌سازی)", Color(0xFF1E8449), Color(0xFFEAFAF1))
                            rawStatus in listOf("sent", "shipped", "posted", "ارسال شد", "ارسال شده", "ارسال به پست", "تحویل پست") ->
                                Triple("ارسال شده به پست 📦", Color(0xFF1D4ED8), Color(0xFFEFF6FF))
                            rawStatus in listOf("delivered", "completed", "تحویل شد", "تحویل داده شده") ->
                                Triple("تحویل داده شده ✅", Color(0xFF0F766E), Color(0xFFF0FDFA))
                            rawStatus in listOf("rejected", "cancelled", "رد شد", "لغو شده") ->
                                Triple("لغو شده ❌", Color(0xFFC0392B), Color(0xFFFDEDEC))
                            else ->
                                Triple("در انتظار تایید پرداخت ⏳", Color(0xFFD68910), Color(0xFFFEF9E7))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = purchase.partName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CodyarTextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "تعداد: ${purchase.quantity} عدد",
                                        fontSize = 13.sp,
                                        color = CodyarTextSecondary
                                    )
                                    Text(
                                        text = "مبلغ کل: ${formatToman(purchase.totalPrice)} تومان",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarRed
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Color(0xFFEAECEF)
                                )

                                Text(
                                    text = "📅 تاریخ سفارش: ${purchase.dateStr}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF718096),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                if (!purchase.address.isNullOrBlank()) {
                                    Text(
                                        text = "📍 آدرس ارسال: ${purchase.address}",
                                        fontSize = 12.sp,
                                        color = CodyarTextSecondary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (!purchase.notes.isNullOrBlank()) {
                                    Text(
                                        text = "📝 توضیحات: ${purchase.notes}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF718096)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
