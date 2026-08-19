package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onPurchasePlan: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Screen navigation state
    // "home", "search", "problems", "store", "profile", "technicians", "orders", "ai_chat"
    var activeTab by remember { mutableStateOf("home") }
    var showSplashScreen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        showSplashScreen = false
    }

    // Dialog & Sheet states
    var showAuthDialog by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf("login") } // "login" or "register"

    var showCartDialog by remember { mutableStateOf(false) }
    var showWebCheckout by remember { mutableStateOf(false) }
    var cartStep by remember { mutableStateOf("cart") } // "cart" or "payment"

    var showPlansDialog by remember { mutableStateOf(false) }

    // Detail view states
    var selectedErrorDetail by remember { mutableStateOf<KodyarErrorCode?>(null) }
    var selectedProblemDetail by remember { mutableStateOf<KodyarCommonProblem?>(null) }

    // Restricted access dialogs
    var showRegisterRequiredDialog by remember { mutableStateOf(false) }
    var showPremiumRequiredDialog by remember { mutableStateOf(false) }

    // Disclaimer / Terms of Use modal states
    var showDisclaimerModal by remember { mutableStateOf(false) }
    var disclaimerChecked by remember { mutableStateOf(false) }
    val isDisclaimerAccepted by viewModel.isDisclaimerAccepted.collectAsState()

    // Form inputs
    var authPhone by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var authName by remember { mutableStateOf("") }
    var authRole by remember { mutableStateOf("customer") } // "customer" or "technician"
    var authCity by remember { mutableStateOf("") }
    var authDistrict by remember { mutableStateOf("") }
    var authSelectedCategories by remember { mutableStateOf(setOf<String>()) }
    var techCategorySearch by remember { mutableStateOf("") }
    var docNationalCardUploaded by remember { mutableStateOf(false) }
    var docTechnicalCertUploaded by remember { mutableStateOf(false) }
    var docTradeLicenseUploaded by remember { mutableStateOf(false) }
    var techDocTitleInput by remember { mutableStateOf("") }
    var techUploadedDocs by remember { mutableStateOf(listOf<String>()) }
    var currentDocTypeToPick by remember { mutableStateOf("") }
    var techUploadedDocUris by remember { mutableStateOf(mapOf<String, Uri>()) }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val docName = if (currentDocTypeToPick.isNotBlank()) currentDocTypeToPick else "مدرک تکنسین"
            techUploadedDocUris = techUploadedDocUris + (docName to uri)
            if (!techUploadedDocs.contains(docName)) {
                techUploadedDocs = techUploadedDocs + docName
            }
            if (docName.contains("کارت ملی")) docNationalCardUploaded = true
            if (docName.contains("فنی") || docName.contains("مدرک")) docTechnicalCertUploaded = true
            if (docName.contains("جواز") || docName.contains("کسب") || docName.contains("اصناف")) docTradeLicenseUploaded = true
            Toast.makeText(context, "تصویر مدرک «$docName» از گالری گوشی انتخاب و پیوست شد", Toast.LENGTH_SHORT).show()
        }
    }

    // Live variables from ViewModel
    val currentUser by viewModel.currentUser.collectAsState()
    val isDatabaseLoading by viewModel.isDatabaseLoading.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    val liveSpareParts by viewModel.liveSpareParts.collectAsState()

    val cartItemsList by viewModel.cart.collectAsState()
    val cartQtyMap by viewModel.cartQty.collectAsState()

    val repairOrders by viewModel.repairOrders.collectAsState()
    val isRepairsLoading by viewModel.isRepairsLoading.collectAsState()
    val isGlobalRefreshing by viewModel.isGlobalRefreshing.collectAsState()

    val freeErrorCount by viewModel.freeErrorCount.collectAsState()
    val freeProblemCount by viewModel.freeProblemCount.collectAsState()

    val appUpdateNotification by viewModel.appUpdateNotification.collectAsState()

    LaunchedEffect(showPlansDialog) {
        if (showPlansDialog) {
            viewModel.fetchSubscriptionPlans()
        }
    }

    val isPremium = currentUser?.subscription?.is_premium == true

    // Trigger RTL context for Persian/Arabic UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = modifier
                    .fillMaxSize()
                    .background(CodyarBg),
                topBar = {
                    Surface(
                        color = CodyarNavy,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_logo),
                                    contentDescription = "لوگوی کدیار ۲۴",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Text(
                                    text = "کدیار ۲۴",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isPremium) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFC9A227), RoundedCornerShape(8.dp))
                                            .height(36.dp)
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "ویژه",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Refresh Button
                                IconButton(
                                    onClick = {
                                        if (!isGlobalRefreshing) {
                                            viewModel.refreshAllAppData { success ->
                                                if (success) {
                                                    Toast.makeText(context, "اطلاعات با موفقیت به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .size(36.dp)
                                ) {
                                    if (isGlobalRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "به‌روزرسانی اطلاعات",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // Cart Button
                                Box {
                                    IconButton(
                                        onClick = {
                                            if (cartItemsList.isNotEmpty()) {
                                                cartStep = "cart"
                                                showCartDialog = true
                                            } else {
                                                Toast.makeText(context, "سبد خرید شما خالی است", Toast.LENGTH_SHORT).show()
                                                activeTab = "store"
                                            }
                                        },
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = "سبد خرید",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    if (cartItemsList.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = (-3).dp, y = (-3).dp)
                                                .background(CodyarRed, CircleShape)
                                                .size(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cartItemsList.size.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Profile Button
                                IconButton(
                                    onClick = {
                                        if (currentUser != null) {
                                            activeTab = "profile"
                                        } else {
                                            authMode = "login"
                                            showAuthDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "پروفایل",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 10.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            data class NavItem(
                                val id: String,
                                val activeIcon: ImageVector,
                                val inactiveIcon: ImageVector,
                                val label: String
                            )

                            val navigationItems = listOf(
                                NavItem("home", Icons.Filled.Home, Icons.Outlined.Home, "خانه"),
                                NavItem("search", Icons.Filled.Search, Icons.Outlined.Search, "کد خطا"),
                                NavItem("problems", Icons.Filled.Build, Icons.Outlined.Build, "مشکلات"),
                                NavItem("store", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart, "فروشگاه"),
                                NavItem("profile", Icons.Filled.Person, Icons.Outlined.Person, "پروفایل")
                            )

                            navigationItems.forEach { item ->
                                val active = activeTab == item.id || (item.id == "profile" && activeTab == "orders") || (item.id == "search" && activeTab == "technicians")

                                val animatedScale by animateFloatAsState(
                                    targetValue = if (active) 1.12f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "iconScale"
                                )

                                val animatedBgColor by animateColorAsState(
                                    targetValue = if (active) CodyarRed.copy(alpha = 0.12f) else Color.Transparent,
                                    label = "bgColor"
                                )

                                val animatedContentColor by animateColorAsState(
                                    targetValue = if (active) CodyarRed else Color(0xFF64748B),
                                    label = "contentColor"
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            selectedErrorDetail = null
                                            selectedProblemDetail = null
                                            if (item.id == "search") {
                                                viewModel.setShowOnlySaved(false)
                                            }
                                            activeTab = item.id
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(54.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(animatedBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (active) item.activeIcon else item.inactiveIcon,
                                            contentDescription = item.label,
                                            tint = animatedContentColor,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .scale(animatedScale)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = item.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = animatedContentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                val isOnline by viewModel.isOnline.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(CodyarBg)
                ) {
                    if (!isOnline) {
                        Surface(
                            color = Color(0xFFD97706),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "بدون اتصال — نمایش آخرین داده ذخیره‌شده",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (activeTab) {
                        "home" -> HomeScreen(
                            viewModel = viewModel,
                            onNavigateToSearch = { activeTab = "search" },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            onShowPlans = { showPlansDialog = true },
                            onOpenErrorCode = { err ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueErrorCodesViewed()
                                    val codeKey = "${err.brand}_${err.category}_${err.code}"
                                    if (isPremium || viewedSet.contains(codeKey) || viewedSet.size < 2) {
                                        viewModel.recordErrorCodeView(codeKey)
                                        selectedErrorDetail = err
                                        activeTab = "search"
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            }
                        )
                        "search" -> SearchScreen(
                            viewModel = viewModel,
                            selectedErrorDetail = selectedErrorDetail,
                            onSelectError = { err ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueErrorCodesViewed()
                                    val codeKey = "${err.brand}_${err.category}_${err.code}"
                                    if (isPremium || viewedSet.contains(codeKey) || viewedSet.size < 2) {
                                        viewModel.recordErrorCodeView(codeKey)
                                        selectedErrorDetail = err
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            },
                            onBack = { selectedErrorDetail = null },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            isPremium = isPremium,
                            freeErrorCount = freeErrorCount,
                            onShowPlans = { showPlansDialog = true }
                        )
                        "problems" -> ProblemsScreen(
                            viewModel = viewModel,
                            liveProblems = viewModel.liveCommonProblems,
                            selectedProblemDetail = selectedProblemDetail,
                            onSelectProblem = { prob ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueProblemsViewed()
                                    val problemKey = "${prob.brand}_${prob.category}_${prob.title}"
                                    if (isPremium || viewedSet.contains(problemKey) || viewedSet.size < 1) {
                                        viewModel.recordProblemView(problemKey)
                                        selectedProblemDetail = prob
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            },
                            onBack = { selectedProblemDetail = null },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            isPremium = isPremium,
                            freeProblemCount = freeProblemCount,
                            onShowPlans = { showPlansDialog = true }
                        )
                        "store" -> StoreScreen(
                            viewModel = viewModel,
                            parts = liveSpareParts,
                            cartItems = cartItemsList,
                            onAddToCart = { viewModel.addToCart(it) }
                        )
                        "profile" -> ProfileScreen(
                            viewModel = viewModel,
                            currentUser = currentUser,
                            onShowAuth = {
                                authMode = "login"
                                showAuthDialog = true
                            },
                            onShowPlans = { showPlansDialog = true },
                            onNavigateToOrders = {
                                viewModel.loadRepairs()
                                activeTab = "orders"
                            },
                            onNavigateToSaved = {
                                viewModel.setShowOnlySaved(true)
                                activeTab = "search"
                            },
                            onShowDisclaimer = {
                                showDisclaimerModal = true
                            }
                        )
                        "technicians" -> TechniciansScreen(
                            viewModel = viewModel,
                            liveTechs = viewModel.liveTechnicians,
                            currentUser = currentUser,
                            onShowAuth = {
                                authMode = "login"
                                showAuthDialog = true
                            }
                        )
                        "orders" -> OrdersScreen(
                            viewModel = viewModel,
                            repairOrders = repairOrders,
                            isRepairsLoading = isRepairsLoading,
                            onBack = { activeTab = "profile" },
                            onNavigateToTechs = { activeTab = "technicians" }
                        )
                        "ai_chat" -> AiChatScreen(viewModel = viewModel)
                    }
                }
            }

            // --- AUTH DIALOG ---
            AuthDialog(
                showAuthDialog = showAuthDialog,
                onDismiss = { showAuthDialog = false },
                authMode = authMode,
                onAuthModeChange = { authMode = it },
                authRole = authRole,
                onAuthRoleChange = { authRole = it },
                authPhone = authPhone,
                onPhoneChange = { authPhone = it },
                authPassword = authPassword,
                onPasswordChange = { authPassword = it },
                isPasswordVisible = isPasswordVisible,
                onPasswordVisibleChange = { isPasswordVisible = it },
                authName = authName,
                onNameChange = { authName = it },
                authCity = authCity,
                onCityChange = { authCity = it },
                authDistrict = authDistrict,
                onDistrictChange = { authDistrict = it },
                authSelectedCategories = authSelectedCategories,
                onCategoriesChange = { authSelectedCategories = it },
                techCategorySearch = techCategorySearch,
                onCategorySearchChange = { techCategorySearch = it },
                techUploadedDocs = techUploadedDocs,
                onUploadedDocsChange = { techUploadedDocs = it },
                techUploadedDocUris = techUploadedDocUris,
                onUploadedDocUrisChange = { techUploadedDocUris = it },
                techDocTitleInput = techDocTitleInput,
                onDocTitleInputChange = { techDocTitleInput = it },
                currentDocTypeToPick = currentDocTypeToPick,
                onDocTypeToPickChange = { currentDocTypeToPick = it },
                docPickerLauncher = docPickerLauncher,
                viewModel = viewModel,
                isAuthLoading = isAuthLoading,
                authError = authError
            )

            // --- CART DIALOG ---
            CartDialog(
                showCartDialog = showCartDialog,
                onDismiss = { showCartDialog = false },
                cartItemsList = cartItemsList,
                cartQtyMap = cartQtyMap,
                liveSpareParts = liveSpareParts,
                currentUser = currentUser,
                viewModel = viewModel,
                onShowAuth = {
                    authMode = "login"
                    showAuthDialog = true
                }
            )

            // --- AUTHENTICATED KODYAR24 WEBSITE CHECKOUT ---
            WebCheckoutDialog(
                showWebCheckout = showWebCheckout,
                onDismiss = { showWebCheckout = false },
                sessionToken = viewModel.getSessionToken() ?: ""
            )

            // --- SUBSCRIPTION PLANS DIALOG ---
            SubscriptionPlansDialog(
                showPlansDialog = showPlansDialog,
                onDismiss = { showPlansDialog = false },
                viewModel = viewModel,
                currentUser = currentUser,
                onShowAuth = {
                    authMode = "login"
                    showAuthDialog = true
                },
                onPurchasePlan = onPurchasePlan
            )

            // --- LIVE WEBSITE UPDATE NOTIFICATION DIALOG ---
            UpdateNotificationDialog(
                updates = appUpdateNotification,
                onDismiss = { viewModel.dismissUpdateNotification() }
            )

            // --- REGISTRATION REQUIRED DIALOG ---
            RegisterRequiredDialog(
                showRegisterRequiredDialog = showRegisterRequiredDialog,
                onDismiss = { showRegisterRequiredDialog = false },
                onNavigateToAuth = {
                    authMode = "register"
                    showAuthDialog = true
                }
            )

            // --- PREMIUM REQUIRED DIALOG ---
            PremiumRequiredDialog(
                showPremiumRequiredDialog = showPremiumRequiredDialog,
                onDismiss = { showPremiumRequiredDialog = false },
                onShowPlans = { showPlansDialog = true }
            )

            // --- BAZAAR APP UPDATE DIALOG ---
            val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
            val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()

            if (showUpdateDialog) {
                AppUpdateDialog(
                    updateInfo = appUpdateInfo,
                    onDismiss = { viewModel.dismissUpdateDialog() },
                    onUpdateClick = {
                        openBazaarUpdate(context)
                        viewModel.dismissUpdateDialog()
                    }
                )
            }

            // --- DISCLAIMER & TERMS OF USE DIALOG ---
            DisclaimerDialog(
                showDisclaimerModal = showDisclaimerModal,
                isDisclaimerAccepted = isDisclaimerAccepted,
                showSplashScreen = showSplashScreen,
                disclaimerChecked = disclaimerChecked,
                onDisclaimerCheckedChange = { disclaimerChecked = it },
                onAccept = {
                    viewModel.acceptDisclaimer()
                    showDisclaimerModal = false
                }
            )

            // --- SPLASH SCREEN ---
            SplashScreenOverlay(showSplashScreen = showSplashScreen)
        }
    }
}
}
