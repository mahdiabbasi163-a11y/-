package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KodyarCommonProblem
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    viewModel: AssistantViewModel,
    liveProblems: StateFlow<List<KodyarCommonProblem>>,
    selectedProblemDetail: KodyarCommonProblem?,
    onSelectProblem: (KodyarCommonProblem) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit = {},
    isPremium: Boolean,
    freeProblemCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val problemsList by liveProblems.collectAsState()
    var problemsSearchQuery by remember { mutableStateOf("") }

    if (selectedProblemDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Text(
                "مشکلات رایج لوازم خانگی",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CodyarNavy,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search within problems
            OutlinedTextField(
                value = problemsSearchQuery,
                onValueChange = { problemsSearchQuery = it },
                placeholder = { Text("جستجو در مشکلات...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Problem Item lists
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filtered = problemsList.filter {
                    problemsSearchQuery.isEmpty() ||
                            (it.title ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                            (it.brand ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                            (it.category ?: "").contains(problemsSearchQuery, ignoreCase = true)
                }

                items(filtered) { prob ->
                    Card(
                        onClick = {
                            if (isPremium) {
                                onSelectProblem(prob)
                            } else {
                                if (freeProblemCount < 1) {
                                    viewModel.useFreeCount("problem") {
                                        onSelectProblem(prob)
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
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF0F2F5), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔧", fontSize = 22.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prob.title ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = CodyarTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                    fontSize = 12.sp,
                                    color = CodyarTextSecondary
                                )
                            }

                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = CodyarTextSecondary)
                        }
                    }
                }
            }
        }
    } else {
        // PROBLEM DETAIL VIEW SCREEN
        val prob = selectedProblemDetail
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
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

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CodyarSurface),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodyarNavy)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Text(
                                text = prob.title ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!prob.description.isNullOrBlank()) {
                            Text(
                                text = prob.description,
                                fontSize = 15.sp,
                                color = CodyarTextPrimary,
                                lineHeight = 30.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            )
                        }

                        // Causes
                        val causesList = with(viewModel) { prob.causes.toListOfStrings() }
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

                        // Solutions / Steps
                        val solList = with(viewModel) { prob.steps.toListOfStrings() }
                        if (solList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("راه‌حل و مراحل رفع", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CodyarTextPrimary)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    solList.forEachIndexed { i, sol ->
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
                                                text = sol,
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
                            text = "منبع: وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) و تجربیات تکنسین‌های مجرب لوازم خانگی",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

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
                                        category = prob.category,
                                        brand = prob.brand,
                                        model = null,
                                        title = prob.title,
                                        code = null,
                                        description = prob.description,
                                        causes = prob.causes
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
                    }
                }
            }
        }
    }
}
