package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var zoomedPartForImage by remember { mutableStateOf<KodyarSparePart?>(null) }

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
                                // Product Image Container - Fill container with rounded top corners
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(155.dp)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable {
                                            zoomedPartForImage = part
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val finalImg = part.image ?: part.imageUrl ?: ""
                                    if (finalImg.isNotEmpty()) {
                                        AsyncImage(
                                            model = if (finalImg.startsWith("http")) finalImg else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${finalImg.removePrefix("/")}",
                                            contentDescription = part.name,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Zoom indicator badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "بزرگ‌نمایی عکس",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "زوم",
                                                    fontSize = 9.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
                                        .height(36.dp)
                                        .testTag("buy_part_direct_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !outOfStock,
                                    contentPadding = PaddingValues(vertical = 0.dp, horizontal = 6.dp)
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
                                        .size(36.dp)
                                        .background(
                                            color = if (inCart) Color(0xFFEAFAF1) else Color(0xFFF0F2F5),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (inCart) Color(0xFFA9DFBF) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (inCart) Icons.Default.Check else Icons.Default.ShoppingCart,
                                        contentDescription = "افزودن به سبد خرید",
                                        tint = if (inCart) Color(0xFF1E8449) else CodyarNavy,
                                        modifier = Modifier.size(18.dp)
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
                                .clickable {
                                    zoomedPartForImage = detailPart
                                }
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
                                // Zoom hint button / badge
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = "بزرگ‌نمایی عکس",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "بزرگ‌نمایی عکس",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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

        // --- PRODUCT IMAGE ZOOM DIALOG (REAL PINCH, PAN, DOUBLE TAP, BACK BUTTON) ---
        val zoomPart = zoomedPartForImage
        if (zoomPart != null) {
            ProductImageZoomDialog(
                part = zoomPart,
                onDismiss = { zoomedPartForImage = null }
            )
        }
    }
}

@Composable
fun ProductImageZoomDialog(
    part: KodyarSparePart,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val rawImg = part.image ?: part.imageUrl ?: ""
    val fullImageUrl = if (rawImg.startsWith("http")) rawImg else "${com.example.data.api.KodyarRetrofitClient.siteRootUrl}/${rawImg.removePrefix("/")}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF00B1120))
        ) {
            // Top Bar with Back / Return Button, Title & Brand
            Surface(
                color = Color(0xDE0F172A),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = part.name ?: "تصویر قطعه",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!part.brand.isNullOrBlank()) {
                                Text(
                                    text = "برند: ${part.brand}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Return Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "بازگشت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Central Zoomable Image Area (Pinch to Zoom, Pan, Double Tap)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 70.dp, bottom = 90.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxX = (size.width * (scale - 1f)) / 2f
                                val maxY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (rawImg.isNotEmpty()) {
                    AsyncImage(
                        model = fullImageUrl,
                        contentDescription = part.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("⚙️", fontSize = 72.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "تصویری برای این قطعه ثبت نشده است",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom Controls Bar with Quick Zoom Buttons & Reset
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Zoom Out (-)
                        IconButton(
                            onClick = {
                                scale = (scale - 0.5f).coerceAtLeast(1f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "کوچک‌نمایی",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Scale Indicator / Reset on click
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                .padding(horizontal = 6.dp)
                        )

                        // Zoom In (+)
                        IconButton(
                            onClick = {
                                scale = (scale + 0.5f).coerceAtMost(5f)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "بزرگ‌نمایی",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Reset (1:1)
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "بازنشانی زوم",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "با دو انگشت یا دوبار لمس می‌توانید روی عکس زوم و جابه‌جا کنید",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
