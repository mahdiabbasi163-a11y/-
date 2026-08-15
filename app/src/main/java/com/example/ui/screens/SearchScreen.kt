package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KodyarErrorCode
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AssistantViewModel,
    selectedErrorDetail: KodyarErrorCode?,
    onSelectError: (KodyarErrorCode) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    isPremium: Boolean,
    freeErrorCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                val normalizedText = normalizeVoiceSearchText(spokenText)
                viewModel.updateSearchFilters(normalizedText, viewModel.selectedBrand.value, viewModel.selectedCategory.value)
            }
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val modelQuery by viewModel.modelQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val savedErrors by viewModel.savedErrors.collectAsState()
    val showOnlySaved by viewModel.showOnlySaved.collectAsState()

    val liveCategories by viewModel.liveCategories.collectAsState()
    val liveBrands by viewModel.liveBrands.collectAsState()

    var showFilterBar by remember { mutableStateOf(false) }

    if (selectedErrorDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchFilters(it, selectedBrand, selectedCategory, modelQuery) },
                placeholder = { Text("کد خطا، برند، دستگاه یا شرح عیب...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchFilters("", selectedBrand, selectedCategory, modelQuery) }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردن", tint = Color(0xFF64748B))
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "لطفاً عیب یا کد خطا را بگویید...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "سیستم صوتی روی این دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "جستجوی صوتی", tint = CodyarRed)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showOnlySaved) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(CodyarNavy.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Text("درحال نمایش کدهای ذخیره شده شما", fontSize = 12.sp, color = CodyarTextPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { viewModel.setShowOnlySaved(false) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("نمایش همه کدها", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable Filter Chip
            Card(
                onClick = { showFilterBar = !showFilterBar },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEAECEF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = if (showFilterBar) CodyarRed else CodyarTextPrimary
                        )
                        Text(
                            text = "فیلتر پیشرفته",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (showFilterBar) CodyarRed else CodyarTextPrimary
                        )
                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(CodyarRed, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text("فعال", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Icon(
                        imageVector = if (showFilterBar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CodyarTextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showFilterBar) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Side-by-side Dropdowns for Device Category and Brand
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "نوع دستگاه:",
                                    selectedValue = selectedCategory,
                                    options = liveCategories,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, selectedBrand, it, modelQuery) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "برند:",
                                    selectedValue = selectedBrand,
                                    options = liveBrands,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, it, selectedCategory, modelQuery) }
                                )
                            }
                        }

                        // Model Filter TextField and Dropdown
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "فیلتر مدل (دقت بیشتر):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CodyarTextPrimary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            val availableModels = remember(selectedBrand, selectedCategory, viewModel.liveErrorCodes.collectAsState().value) {
                                viewModel.getAvailableModelsFor(selectedBrand, selectedCategory)
                            }
                            
                            var modelExpanded by remember { mutableStateOf(false) }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = modelQuery,
                                    onValueChange = { 
                                        viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, it)
                                        modelExpanded = true
                                    },
                                    placeholder = { Text("مثلاً: ۲۴۰۰، v12، دایرکت درایو...", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (modelQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, "") }) {
                                                    Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            IconButton(onClick = { modelExpanded = !modelExpanded }) {
                                                Icon(
                                                    imageVector = if (modelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "نمایش لیست",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                modelExpanded = true
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Right),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CodyarNavy,
                                        unfocusedBorderColor = Color(0xFFDDE1E7),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC)
                                    )
                                )
                                
                                val filteredModels = remember(availableModels, modelQuery) {
                                    if (modelQuery.isEmpty() || modelQuery == "همه") {
                                        availableModels
                                    } else {
                                        val normQuery = viewModel.canonicalModel(modelQuery)
                                        availableModels.filter { 
                                            it == "همه" || viewModel.canonicalModel(it).contains(normQuery, ignoreCase = true)
                                        }
                                    }
                                }
                                
                                if (filteredModels.isNotEmpty() && modelExpanded) {
                                    DropdownMenu(
                                        expanded = modelExpanded,
                                        onDismissRequest = { modelExpanded = false },
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .heightIn(max = 240.dp)
                                            .background(CodyarSurface)
                                    ) {
                                        filteredModels.forEach { modelOpt ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = modelOpt,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = TextAlign.Right
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, if (modelOpt == "همه") "" else modelOpt)
                                                    modelExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.updateSearchFilters("", "همه", "همه", "") },
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.align(Alignment.End),
                                border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                            ) {
                                Text("× پاک کردن فیلترها", fontSize = 11.sp, color = CodyarTextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${searchResults.size} نتیجه یافت شد",
                fontSize = 11.sp,
                color = CodyarTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Results List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(searchResults) { err ->
                    val severityLevel = err.hazardLevel ?: "medium"
                    val (color, bg, label) = when (severityLevel) {
                        "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "خطرناک")
                        "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "کم‌خطر")
                        else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPremium) {
                                    onSelectError(err)
                                } else {
                                    if (freeErrorCount < 1) {
                                        viewModel.useFreeCount("error") {
                                            onSelectError(err)
                                        }
                                    } else {
                                        onShowPlans()
                                    }
                                }
                            },
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
                                    .size(44.dp)
                                    .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.code ?: "",
                                    fontSize = 12.sp,
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

                if (searchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("❌", fontSize = 36.sp)
                            Text("نتیجه‌ای پیدا نشد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("کد خطا یا برند را بررسی کنید", fontSize = 12.sp, color = CodyarTextSecondary)
                        }
                    }
                }
            }
        }
    } else {
        // ERROR DETAIL SCREEN
        val err = selectedErrorDetail
        val isBookmarked = savedErrors.any { it.code == err.code && it.brand == err.brand && it.category == err.category }

        val severityLevel = err.hazardLevel ?: "medium"
        val (themeColor, bgText, titleText) = when (severityLevel) {
            "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "بحرانی / خطرناک")
            "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "آسان / کم‌خطر")
            else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CodyarSurface,
                        contentColor = CodyarTextPrimary
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bookmark Icon
                IconButton(
                    onClick = {
                        viewModel.toggleSavedError(err.code ?: "", err.brand ?: "", err.category ?: "", isBookmarked)
                        Toast.makeText(
                            context,
                            if (isBookmarked) "از ذخیره‌شده‌ها حذف شد" else "به ذخیره‌شده‌ها اضافه شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "ذخیره",
                        tint = if (isBookmarked) Color.Red else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header colored based on hazard level
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColor)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.code ?: "",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = err.title ?: "بررسی ارور ${err.code}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Content Padding
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!err.description.isNullOrBlank()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD68910), modifier = Modifier.size(14.dp))
                                    Text("توضیح خطا", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Text(
                                    text = err.description,
                                    fontSize = 15.sp,
                                    color = CodyarTextPrimary,
                                    lineHeight = 30.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFEF9E7), RoundedCornerShape(10.dp))
                                        .border(BorderStroke(1.dp, Color(0xFFFEF9E7)))
                                        .padding(14.dp)
                                )
                            }
                        }

                        // Causes List
                        val causesList = with(viewModel) { err.causes.toListOfStrings() }
                        if (causesList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                    Text("علت‌های احتمالی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    causesList.forEachIndexed { i, cause ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFDF0EE), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "${i + 1}.",
                                                color = Color(0xFFC0392B),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cause,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Steps list
                        val stepsList = with(viewModel) { err.steps.toListOfStrings() }
                        if (stepsList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("مراحل رفع مشکل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    stepsList.forEachIndexed { i, step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEAFAF1), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color(0xFF1E8449), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = (i + 1).toString(),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = step,
                                                fontSize = 15.sp,
                                                color = CodyarTextPrimary,
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت کدیار۲۴ (kodyar24.ir) و دفترچه راهنمای رسمی شرکت سازنده ${err.brand}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        // Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToTechnicians,
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("اعزام تکنسین")
                            }

                            Button(
                                onClick = {
                                    val partQuery = buildSparePartQueryForError(
                                        category = err.category,
                                        brand = err.brand,
                                        model = err.model,
                                        title = err.title,
                                        code = err.code,
                                        description = err.description,
                                        causes = err.causes
                                    )
                                    if (partQuery.isNotBlank()) {
                                        viewModel.setStoreSearchQuery(partQuery)
                                    }
                                    onNavigateToStore()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E8449)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("قطعات یدکی")
                            }
                        }

                        // Copy Button
                        TextButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(
                                        "${err.brand} - ${err.category}\nکد خطا: ${err.code}\nشرح عیب: ${err.description ?: ""}\nمراحل حل: ${stepsList.joinToString("\n")}"
                                    )
                                )
                                Toast.makeText(context, "اطلاعات عیب‌یابی کپی شد!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("کپی متن خطایابی", fontWeight = FontWeight.Bold, color = CodyarRed)
                        }
                    }
                }
            }
        }
    }
}
