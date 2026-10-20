package com.wafyro.hishabledger

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Remembers whether the first-run guide has already been shown. */
object OnboardingStore {
    private fun prefs(context: Context) = context.getSharedPreferences("hishab_onboarding", Context.MODE_PRIVATE)
    fun isSeen(context: Context): Boolean = prefs(context).getBoolean("seen", false)
    fun markSeen(context: Context) { prefs(context).edit().putBoolean("seen", true).apply() }
}

private data class GuidePage(
    val icon: ImageVector,
    val titleEn: String, val titleBn: String,
    val bodyEn: String, val bodyBn: String
)

private val guidePages = listOf(
    GuidePage(
        Icons.Outlined.AccountBalanceWallet,
        "Welcome to Hishab Ledger", "Hishab Ledger-এ স্বাগতম",
        "Keep track of dues and payments with shops and people. No internet and no sign-up needed — your data stays on your phone.",
        "দোকান বা মানুষের সাথে বাকি আর পরিশোধের হিসাব রাখুন। ইন্টারনেট লাগে না, অ্যাকাউন্টও খুলতে হয় না। সব তথ্য শুধু আপনার ফোনে থাকে।"
    ),
    GuidePage(
        Icons.Outlined.PersonAdd,
        "Start with an account", "প্রথমে একটি হিসাব খুলুন",
        "Tap the + button at the bottom of the home screen. Choose Shop or Person, type the name, and add any previous due if there is one.",
        "হোম স্ক্রিনের নিচের মাঝখানের + বাটন চাপুন। দোকান নাকি ব্যক্তি বেছে নাম লিখুন। আগের কোনো বাকি থাকলে সেটাও লিখে দিতে পারেন।"
    ),
    GuidePage(
        Icons.Outlined.ReceiptLong,
        "Record dues and payments", "বাকি ও পেমেন্ট লিখুন",
        "Open an account. \"Add Due\" increases the balance, \"Add Payment\" reduces it. Made a mistake? Delete the entry — you can restore it from Recently Deleted for 30 days.",
        "হিসাবটি খুলুন। \"বাকি যোগ করুন\" চাপলে বাকি বাড়ে, \"পেমেন্ট যোগ করুন\" চাপলে কমে। ভুল হলে এন্ট্রি মুছুন, ৩০ দিন পর্যন্ত \"সম্প্রতি মুছে ফেলা\" থেকে ফিরিয়ে আনা যাবে।"
    ),
    GuidePage(
        Icons.Outlined.CloudSync,
        "Back up your ledger", "ব্যাকআপ নিয়ে রাখুন",
        "If the phone is lost or the app is uninstalled, the data cannot be recovered. Save a backup file regularly from Settings > Backup & Data.",
        "ফোন হারালে বা অ্যাপ মুছে ফেললে তথ্য ফিরে পাওয়া যায় না। তাই নিয়মিত সেটিংস > Backup & Data থেকে ব্যাকআপ ফাইল সংরক্ষণ করুন।"
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val bn = AppLanguage.isBangla
    val pagerState = rememberPagerState(pageCount = { guidePages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == guidePages.lastIndex

    // Back steps to the previous page first; on the first page it closes the guide.
    BackHandler {
        if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        else onFinish()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        // Keep the top area clean: no duplicate app icon on onboarding.
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!isLast) TextButton(onClick = onFinish) { Text(if (bn) "এড়িয়ে যান" else "Skip") }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = guidePages[index]
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(380)) + slideInVertically(initialOffsetY = { it / 14 }, animationSpec = tween(380))
            ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    if (bn) page.titleBn else page.titleEn,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (bn) page.bodyBn else page.bodyEn,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                // Language choice only on the first page, because the app starts in English.
                if (index == 0) {
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !bn, onClick = { AppLanguage.set(context, false) }, label = { Text("English") })
                        FilterChip(selected = bn, onClick = { AppLanguage.set(context, true) }, label = { Text("বাংলা") })
                    }
                }
            }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
            repeat(guidePages.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier.padding(horizontal = 4.dp)
                        .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            }
        }

        Button(
            onClick = {
                if (isLast) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Text(
                if (isLast) { if (bn) "শুরু করুন" else "Get started" } else { if (bn) "পরের ধাপ" else "Next" },
                fontWeight = FontWeight.Bold
            )
        }
    }
}
