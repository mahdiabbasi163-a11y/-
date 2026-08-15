package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.KodyarUser
import com.example.ui.AssistantViewModel

data class QuadProfileMenu(
    val icon: ImageVector,
    val tint: Color,
    val label: String,
    val action: () -> Unit
)

@Composable
fun ProfileScreen(
    viewModel: AssistantViewModel,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit,
    onShowPlans: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToSaved: () -> Unit,
    onShowDisclaimer: () -> Unit = {}
) {
    val context = LocalContext.current
    var showLogoutConfirmationDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        if (currentUser == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("👤", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("وارد حساب خود شوید", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarTextPrimary)
                Text("جهت دسترسی به اشتراک ویژه و مدیریت درخواست‌های خود", fontSize = 13.sp, color = CodyarTextSecondary, modifier = Modifier.padding(vertical = 6.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onShowAuth,
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("ورود / ثبت‌نام")
                }
            }
        } else {
            val isPremium = currentUser.subscription?.is_premium == true
            val rawExpiry = currentUser.subscription?.expiry_date ?: ""
            val expiry = if (rawExpiry.startsWith("20") && rawExpiry.length >= 10) {
                convertGregorianToJalali(rawExpiry.take(10))
            } else {
                convertGregorianToJalali(rawExpiry)
            }

            // Main User details card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val userAvatar = currentUser.resolvedAvatarUrl
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(CodyarNavy, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!userAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = userAvatar,
                                    contentDescription = currentUser.full_name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column {
                            Text(currentUser.full_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarTextPrimary)
                            Text(currentUser.phone, fontSize = 12.sp, color = CodyarTextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    if (isPremium) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Column {
                                    Text("اشتراک ویژه فعال است", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    if (expiry.isNotEmpty()) {
                                        Text("انقضا تا تاریخ: $expiry", fontSize = 10.sp, color = Color(0xFF1B5E20))
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                                    Text("اشتراک فعال منقضی یا ناموجود است", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                }
                                Button(
                                    onClick = onShowPlans,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("ارتقا به اشتراک ویژه")
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION: Referral & Invite Code Card ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
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
                        Text("🎁", fontSize = 18.sp)
                        Text(
                            text = "سیستم معرفی و کد تخفیف همکاران",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CodyarTextPrimary
                        )
                    }

                    val isTechnician = currentUser.role == "technician"
                    if (isTechnician) {
                        val referralCode = "CODYAR-${currentUser.phone.takeLast(6)}"
                        val clipboardManager = LocalClipboardManager.current
                        
                        Text(
                            text = "شما به عنوان تکنسین می‌توانید کد دعوت خود را با دیگر همکاران و مشتریان به اشتراک بگذارید تا از ۲۵٪ تخفیف ویژه خرید اشتراک بهره‌مند شوند.",
                            fontSize = 11.sp,
                            color = CodyarTextSecondary,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CodyarBg, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("کد دعوت اختصاصی شما:", fontSize = 10.sp, color = Color.Gray)
                                Text(referralCode, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CodyarNavy)
                            }
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(referralCode))
                                    Toast.makeText(context, "کد دعوت کپی شد: $referralCode", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("کپی کردن کد", fontSize = 11.sp)
                            }
                        }
                    } else {
                        val appliedCode by viewModel.appliedReferralCode.collectAsState()
                        var codeInput by remember { mutableStateOf("") }

                        Text(
                            text = "اگر کد معرف از همکاران تکنسین دارید، آن را وارد کنید تا ۲۵٪ تخفیف ویژه خرید تمامی اشتراک‌های کدیار۲۴ برای شما اعمال شود.",
                            fontSize = 11.sp,
                            color = CodyarTextSecondary,
                            lineHeight = 18.sp
                        )

                        if (appliedCode != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "کد اعمال شده: $appliedCode (تخفیف ۲۵٪ فعال است)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                TextButton(
                                    onClick = { viewModel.removeReferralCode() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("حذف کد", fontSize = 11.sp, color = CodyarRed)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = { codeInput = it },
                                    placeholder = { Text("کد معرف را وارد کنید", fontSize = 11.sp) },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CodyarNavy,
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (codeInput.isNotBlank()) {
                                            val success = viewModel.applyReferralCode(codeInput)
                                            if (success) {
                                                Toast.makeText(context, "کد معرف با ۲۵٪ تخفیف اعمال شد", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "کد دعوت نامعتبر است (حداقل ۴ کاراکتر)", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("ثبت و اعمال", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Menu choices
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val menuItems = listOf(
                        QuadProfileMenu(Icons.Default.Star, Color(0xFFC9A227), "اشتراک و پلن‌ها", onShowPlans),
                        QuadProfileMenu(Icons.Default.Build, Color(0xFF1E8449), "سفارشات تعمیر من", onNavigateToOrders),
                        QuadProfileMenu(Icons.Default.Favorite, Color(0xFF2563EB), "کدهای ذخیره شده", onNavigateToSaved),
                        QuadProfileMenu(Icons.Default.ExitToApp, Color(0xFFC0392B), "خروج از حساب کاربری", { showLogoutConfirmationDialog = true })
                    )

                    menuItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.action() }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(item.tint.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = item.label, tint = item.tint, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = item.label,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CodyarTextPrimary
                            )
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color(0xFFD1D5DB))
                        }

                        if (index < menuItems.size - 1) {
                            HorizontalDivider(color = Color(0xFFF7F8FA))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(16.dp))
                    Text(
                        "⚖️ مالکیت فکری و منابع محتوایی",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                }
                Text(
                    "تمامی محتوای علمی، کدهای خطا، راهکارهای رفع عیب و ایرادات فنی ارائه‌شده در این اپلیکیشن، متعلق به تیم فنی وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) می‌باشد. این اطلاعات بر اساس تخصص تکنسین‌های مجرب کدیار۲۴ و استناد به دفترچه‌های راهنما و کاتالوگ‌های رسمی شرکت‌های سازنده لوازم خانگی تدوین و به صورت اختصاصی جهت استفاده همکاران یکپارچه‌سازی شده است.",
                    fontSize = 10.sp,
                    color = Color(0xFF166534),
                    textAlign = TextAlign.Right,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = Color(0xFFDCFCE7), thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🌐 وب‌سایت مرجع",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Surface(
                        onClick = { onShowDisclaimer() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📜 سلب مسئولیت",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:info@kodiar24.ir"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "📧 پشتیبانی",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogoutConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmationDialog = false },
            title = {
                Text(
                    text = "خروج از حساب کاربری",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CodyarNavy
                )
            },
            text = {
                Text(
                    text = "آیا اطمینان دارید که می‌خواهید از حساب کاربری خود خارج شوید؟",
                    fontSize = 14.sp,
                    color = CodyarTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmationDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("خروج", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmationDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("انصراف", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White
        )
    }
}
