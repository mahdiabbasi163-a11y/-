package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun SplashScreenOverlay(showSplashScreen: Boolean) {
    AnimatedVisibility(
        visible = showSplashScreen,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(400))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
        val logoScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logo_scale"
        )

        val loadingRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "loading_rotation"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CodyarNavy),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "لوگوی کدیار۲۴",
                    modifier = Modifier
                        .size(90.dp)
                        .scale(logoScale)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "کدیار۲۴",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "دستیار هوشمند و تخصصی تعمیرات لوازم خانگی",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Prominent Circular Loading Indicator with active continuous rotation
                CircularProgressIndicator(
                    color = CodyarRed,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 3.5.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .rotate(loadingRotation)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "در حال اتصال و دریافت اطلاعات...",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "نسخه ۲.۰.۰ کافه‌بازار • کدیار۲۴",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }
    }
}

