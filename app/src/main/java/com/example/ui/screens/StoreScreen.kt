package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.KodyarSparePart
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    viewModel: AssistantViewModel,
    parts: List<KodyarSparePart>,
    cartItems: List<String>,
    onAddToCart: (String) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.storeSearchQuery.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val cartQtyMap by viewModel.cartQty.collectAsState()
    var selectedPartForDetails by remember { mutableStateOf<KodyarSparePart?>(null) }

    // Smart Persian normalized filtering and scoring logic
    val filteredParts = remember(parts, searchQuery) {
        filterSparePartsByQuery(parts, searchQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "فروشگاه قطعات یدکی",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CodyarNavy
            )
            if (searchQuery.isNotBlank()) {
                TextButton(
                    onClick = { viewModel.clearStoreSearchQuery() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("نمایش همه قطعات", fontSize = 12.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Store Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setStoreSearchQuery(it) },
            placeholder = { Text("جستجوی قطعه، برند (مثلاً بوتان، پکیج)...", fontSize = 12.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CodyarNavy) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearStoreSearchQuery() }) {
                        Icon(Icons.Default.Clear, contentDescription = "پاک کردن", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = CodyarNavy,
                unfocusedBorderColor = Color(0xFFE2E8F0)
            )
        )

        // Active Filter Banner (when navigated from search or error cards)
        if (searchQuery.isNotBlank()) {
            Surface(
                color = Color(0xFFEAFAF1),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFA9DFBF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(16.dp))
                        Text(
                            text = "قطعات مرتبط با: $searchQuery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E8449),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearStoreSearchQuery() },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن فیلتر", tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (parts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📦", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("فروشگاه در حال تکمیل است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("به زودی قطعات اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
            }
        } else if (filteredParts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🔍", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("قطعه‌ای متناسب با «$searchQuery» یافت نشد", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CodyarNavy)
                Spacer(modifier = Modifier.height(6.dp))
                Text("می‌توانید تمام قطعات موجود را مشاهده کنید.", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.clearStoreSearchQuery() },
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("مشاهده همه قطعات فروشگاه")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredParts) { part ->
                    val inCart = cartItems.contains(part.id)
                    val outOfStock = (part.stock ?: 0) <= 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPartForDetails = part }
                            ) {
                                // Product Image Container - Responsive uniform sizing with high contrast background
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(155.dp)
                                        .background(Color(0xFFF8FAFC))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val finalImg = part.image ?: part.imageUrl ?: ""
                                    if (finalImg.isNotEmpty()) {
                                        AsyncImage(
                                            model = if (finalImg.startsWith("http")) finalImg else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${finalImg.removePrefix("/")}",
                                            contentDescription = part.name,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text("⚙️", fontSize = 48.sp)
                                    }
                                }

                                // Info block
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = part.name ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarTextPrimary,
                                        maxLines = 2,
                                        minLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 17.sp
                                    )
                                    Text(
                                        text = part.brand ?: "برند عمومی",
                                        fontSize = 10.sp,
                                        color = CodyarTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (outOfStock) "ناموجود" else "موجود (${part.stock})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (outOfStock) Color(0xFFC0392B) else Color(0xFF1E8449)
                                        )

                                        Text(
                                            text = "${formatToman(part.price ?: 0.0)} ت",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E8449)
                                        )
                                    }
                                }
                            }

                            // Direct purchase and add to cart buttons pinned at the bottom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (!outOfStock) {
                                            selectedPartForDetails = part
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (outOfStock) Color(0xFFF0F2F5) else CodyarNavy,
                                        contentColor = if (outOfStock) CodyarTextSecondary else Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("buy_part_direct_button"),
                                    shape = RoundedCornerShape(7.dp),
                                    enabled = !outOfStock,
                                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = if (outOfStock) "ناموجود" else "خرید قطعه 💳",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { if (!outOfStock) onAddToCart(part.id ?: "") },
                                    enabled = !outOfStock,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = if (inCart) Color(0xFFEAFAF1) else Color(0xFFF0F2F5),
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (inCart) Color(0xFFA9DFBF) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(7.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (inCart) Icons.Default.Check else Icons.Default.ShoppingCart,
                                        contentDescription = "افزودن به سبد خرید",
                                        tint = if (inCart) Color(0xFF1E8449) else CodyarNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PART DETAILS & QUANTITY DIALOG ---
        val detailPart = selectedPartForDetails
        if (detailPart != null) {
            val rawStock = detailPart.stock ?: 0
            val isOutOfStock = rawStock <= 0
            val maxStock = if (rawStock > 0) rawStock else 10
            var selectedQty by remember(detailPart.id) { mutableIntStateOf(if (isOutOfStock) 0 else 1) }
            val unitPrice = detailPart.price ?: 0.0
            val totalPrice = unitPrice * selectedQty

            AlertDialog(
                onDismissRequest = { selectedPartForDetails = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جزئیات و انتخاب تعداد قطعه",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { selectedPartForDetails = null }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val imgUrl = detailPart.image ?: detailPart.imageUrl ?: ""
                            if (imgUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = if (imgUrl.startsWith("http")) imgUrl else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${imgUrl.removePrefix("/")}",
                                    contentDescription = detailPart.name,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text("⚙️", fontSize = 56.sp)
                            }
                        }

                        // Title & Brand
                        Text(
                            text = detailPart.name ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CodyarTextPrimary
                        )
                        if (!detailPart.brand.isNullOrBlank()) {
                            Text(
                                text = "برند: ${detailPart.brand}",
                                fontSize = 12.sp,
                                color = CodyarTextSecondary
                            )
                        }

                        // Stock & Unit Price
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isOutOfStock) "ناموجود" else "موجود در انبار ($rawStock عدد)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOutOfStock) Color(0xFFC0392B) else Color(0xFF1E8449)
                            )
                            Text(
                                text = "${formatToman(unitPrice)} تومان",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E8449)
                            )
                        }

                        if (!isOutOfStock) {
                            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                            // Quantity Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تعداد سفارش:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CodyarTextPrimary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (selectedQty > 1) selectedQty-- },
                                        enabled = selectedQty > 1,
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(6.dp))
                                            .size(32.dp)
                                    ) {
                                        Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = selectedQty.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.width(30.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    IconButton(
                                        onClick = { if (selectedQty < maxStock) selectedQty++ },
                                        enabled = selectedQty < maxStock,
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(6.dp))
                                            .size(32.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Total Price Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مبلغ کل ($selectedQty عدد):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = "${formatToman(totalPrice)} تومان",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isOutOfStock) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (currentUser == null) {
                                        Toast.makeText(context, "لطفاً ابتدا وارد حساب کاربری خود شوید.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val partId = detailPart.id ?: ""
                                        val partName = detailPart.name ?: "قطعه"
                                        selectedPartForDetails = null
                                        viewModel.initiateDirectPartPurchase(
                                            context = context,
                                            partId = partId,
                                            partName = partName,
                                            quantity = selectedQty,
                                            totalPrice = totalPrice
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("خرید مستقیم و پرداخت 💳", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val partId = detailPart.id ?: ""
                                    viewModel.addToCartWithQty(partId, selectedQty)
                                    selectedPartForDetails = null
                                    Toast.makeText(context, "قطعه به سبد خرید اضافه شد 🛒", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("افزودن به سبد خرید 🛒", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { selectedPartForDetails = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("بستن")
                        }
                    }
                },
                dismissButton = {}
            )
        }
    }
}
