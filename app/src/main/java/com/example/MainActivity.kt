package com.example

import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.TatbiqatiViewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: TatbiqatiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Ensure UI direction is strictly Right-To-Left for natural, beautiful Arabic layouts
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TatbiqatiAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun TatbiqatiAppScreen(viewModel: TatbiqatiViewModel) {
    var selectedTab by remember { mutableStateOf("dashboard") } // "dashboard", "chat", "sos"
    val isEmergencyMode = viewModel.isEmergencyMode

    // Auto switch to SOS screen if emergency mode is active
    LaunchedEffect(isEmergencyMode) {
        if (isEmergencyMode) {
            selectedTab = "sos"
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isEmergencyMode) {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                "dashboard" -> DashboardScreen(viewModel = viewModel)
                "chat" -> ChatScreen(viewModel = viewModel)
                "sos" -> EmergencyScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VibrantSurface) // Clean white/surface background
            .border(width = 1.dp, color = VibrantBorder, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavBarItem(
            title = "الرئيسية",
            icon = Icons.Default.Home,
            isSelected = selectedTab == "dashboard",
            tag = "dashboard_tab",
            onClick = { onTabSelected("dashboard") }
        )
        NavBarItem(
            title = "المدرب الذكي",
            icon = Icons.Default.Person,
            isSelected = selectedTab == "chat",
            tag = "chat_tab",
            onClick = { onTabSelected("chat") }
        )
        NavBarItem(
            title = "إنقاذ SOS",
            icon = Icons.Default.Warning,
            isSelected = selectedTab == "sos",
            tag = "sos_tab",
            isWarning = true,
            onClick = { onTabSelected("sos") }
        )
    }
}

@Composable
fun NavBarItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    tag: String,
    isWarning: Boolean = false,
    onClick: () -> Unit
) {
    val activeColor = if (isWarning) VibrantSOSBorder else VibrantPrimary
    val inactiveColor = VibrantTextMedium.copy(alpha = 0.6f)
    val itemColor = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = itemColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = itemColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// --- SCREEN 1: DASHBOARD ---
@Composable
fun DashboardScreen(viewModel: TatbiqatiViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val relapseHistory by viewModel.relapseHistory.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showRelapseDialog by remember { mutableStateOf(false) }

    val userName = profile?.name ?: "البطل"
    val startMillis = profile?.challengeStartDate ?: System.currentTimeMillis()
    val isAthlete = profile?.isAthlete ?: true

    // Calculate Streak
    val diffMillis = System.currentTimeMillis() - startMillis
    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt() + 1
    val currentDay = if (diffDays < 1) 1 else if (diffDays > 90) 90 else diffDays

    // Determine Phase
    val (phaseTitle, phaseDesc, phaseIcon, habitsList) = when {
        currentDay <= 30 -> Tuple4(
            "المرحلة الأولى: التأسيس",
            "ترسيخ الروتين اليومي الأساسي وحماية الدماغ من المثيرات.",
            "🛡️",
            listOf(
                HabitItem("morning_exercise", "التمارين الصباحية السريعة (5 دقائق) 🏋️‍♂️"),
                HabitItem("water_3l", "شرب 3 لتر ماء طوال اليوم 💧"),
                HabitItem("no_isolation", "تجنب العزلة بالهاتف لأكثر من 30 دقيقة 📱"),
                HabitItem("journal_triggers", "كتابة وتدوين المثيرات واليوميات 📝")
            )
        )
        currentDay <= 60 -> Tuple4(
            "المرحلة الثانية: التثبيت",
            "زيادة التمارين، بناء مهارات حقيقية، وتنظيم مستويات الاستجابة.",
            "🦾",
            listOf(
                HabitItem("workout_heavy", "ممارسة تمرين مكثف بالجيم أو الجري (30-45 دقيقة) 💪"),
                HabitItem("skill_learning", "تعلم مهارة جديدة (برمجة، لغات، تصميم) 💻"),
                HabitItem("meditation_10", "تأمل وتنفس عميق لتخفيف القلق (10 دقائق) 🧘"),
                HabitItem("bedtime_reading", "استبدال عادة تصفح الهاتف بالقراءة قبل النوم 📚")
            )
        )
        else -> Tuple4(
            "المرحلة الثالثة: الاستدامة",
            "جني الأرباح، تثبيت المكاسب الدائمة، والانطلاق للأهداف الكبرى.",
            "🏆",
            listOf(
                HabitItem("gains_review", "مراجعة وتسجيل المكاسب الجسدية والعقلية 🌟"),
                HabitItem("help_others", "دعم ومساعدة الشباب الآخرين في رحلاتهم 🤝"),
                HabitItem("locked_routine", "تأمين الروتين اليومي المستدام والالتزام الكامل 🔒"),
                HabitItem("major_challenge", "التدريب لهدف رياضي أو أكاديمي عملاق 🏅")
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scroll"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Profile Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(VibrantPrimary, VibrantPrimaryGradientEnd)))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مرحباً يا بطل، $userName 👋",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isAthlete) "التركيز: بناء القوة الرياضية والتعافي العضلي 💪" else "التركيز: التفوق الدراسي والعقل الحديدي 📚",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .size(36.dp)
                                .testTag("edit_profile_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "تعديل الملف الشخصي",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress circular with day count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(90.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { currentDay / 90f },
                                modifier = Modifier.size(90.dp),
                                color = Color.White,
                                strokeWidth = 8.dp,
                                trackColor = Color.White.copy(alpha = 0.25f),
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$currentDay",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "يوم",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$phaseIcon $phaseTitle",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = phaseDesc,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Daily Motivational Banner Quote
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VibrantSecondary),
                border = BorderStroke(1.dp, Color(0xFFA6C8FF))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "«إذا تعثرت اليوم، فهذا لا يعني أنك خسرت كل شيء. تعثرك هو مجرد جولة لإعادة المعايرة العصبية وضبط تكتيك المعركة!»",
                        color = VibrantTextDark,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Checklist of Habits based on current Phase
        item {
            Text(
                text = "العادات اليومية الموصى بها لتجاوز الرغبة 📋",
                color = VibrantTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        items(habitsList) { habit ->
            val checkedListCsv = profile?.completedHabitsCsv ?: ""
            val isChecked = checkedListCsv.split(",").contains(habit.id)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleHabit(habit.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isChecked) VibrantSecondary else VibrantSurface
                ),
                border = BorderStroke(1.dp, if (isChecked) VibrantPrimary else VibrantBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = habit.title,
                        color = VibrantTextDark,
                        fontSize = 12.sp,
                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { viewModel.toggleHabit(habit.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VibrantPrimary,
                            uncheckedColor = VibrantTextMedium.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("habit_chk_${habit.id}")
                    )
                }
            }
        }

        // Relapse action button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { showRelapseDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("log_relapse_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibrantSOSBg,
                    contentColor = VibrantSOSBorder
                ),
                border = BorderStroke(1.dp, VibrantSOSBorder)
            ) {
                Text(
                    text = "سجل انتكاسة مؤقتة (معالجة عصبية بلا توبيخ) 🚨",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // History logs
        if (relapseHistory.isNotEmpty()) {
            item {
                Text(
                    text = "سجل معارك الصمود العظيم (مستخلصات التعلم) 🧠",
                    color = VibrantTextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            items(relapseHistory) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VibrantSurface),
                    border = BorderStroke(1.dp, VibrantBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "صمود دام لـ ${log.streakDays} يوماً",
                                color = VibrantPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(log.timestamp)),
                                color = VibrantTextMedium,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "المحفز الذي حدث: ${log.reason}",
                            color = VibrantTextDark,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تكتيك التعديل للمستقبل: ${log.lessonLearned}",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Edit profile and streak days dialog
    if (showEditProfileDialog) {
        var editName by remember { mutableStateOf(userName) }
        var editIsAthlete by remember { mutableStateOf(isAthlete) }
        var offsetDays by remember { mutableStateOf("0") }

        Dialog(onDismissRequest = { showEditProfileDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VibrantSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VibrantBorder)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "تعديل إعدادات الرحلة والبطولة ⚙️",
                        color = VibrantTextDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "اسمك الحركي:",
                        color = VibrantTextMedium,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VibrantTextDark,
                            unfocusedTextColor = VibrantTextDark,
                            focusedBorderColor = VibrantPrimary,
                            unfocusedBorderColor = VibrantBorder,
                            focusedContainerColor = VibrantBackground,
                            unfocusedContainerColor = VibrantBackground
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "مسارك الأساسي لتوجيه الطاقة:",
                        color = VibrantTextMedium,
                        fontSize = 12.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { editIsAthlete = true }
                    ) {
                        RadioButton(
                            selected = editIsAthlete,
                            onClick = { editIsAthlete = true },
                            colors = RadioButtonDefaults.colors(selectedColor = VibrantPrimary)
                        )
                        Text("الرياضة وبناء العضلات 💪", color = VibrantTextDark, fontSize = 12.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { editIsAthlete = false }
                    ) {
                        RadioButton(
                            selected = !editIsAthlete,
                            onClick = { editIsAthlete = false },
                            colors = RadioButtonDefaults.colors(selectedColor = VibrantPrimary)
                        )
                        Text("الدراسة والتفوق الأكاديمي 📚", color = VibrantTextDark, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "تعديل يوم الصمود الحالي (إذا كنت قد بدأت الصمود مسبقاً):",
                        color = VibrantPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = offsetDays,
                        onValueChange = { offsetDays = it.filter { char -> char.isDigit() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_offset_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VibrantTextDark,
                            unfocusedTextColor = VibrantTextDark,
                            focusedBorderColor = VibrantPrimary,
                            unfocusedBorderColor = VibrantBorder,
                            focusedContainerColor = VibrantBackground,
                            unfocusedContainerColor = VibrantBackground
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditProfileDialog = false }) {
                            Text("إلغاء", color = VibrantTextMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateProfile(editName, editIsAthlete)
                                val days = offsetDays.toIntOrNull() ?: 0
                                if (days > 0) {
                                    viewModel.setCustomStartDay(days - 1)
                                }
                                showEditProfileDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VibrantPrimary),
                            modifier = Modifier.testTag("save_profile_btn")
                        ) {
                            Text("حفظ البيانات")
                        }
                    }
                }
            }
        }
    }

    // Relapse Dialog
    if (showRelapseDialog) {
        var relapseReason by remember { mutableStateOf("") }
        var relapseLesson by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showRelapseDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VibrantSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VibrantSOSBorder)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "سجل انتكاستك لنتعلم الدرس وننطلق 🧠",
                        color = VibrantSOSBorder,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يا بطل، سقوطك اليوم هو خطوة صغيرة في تكتيك الحرب الكبرى. بدلاً من جلد الذات، سجل بكل شرف المحفز والدرس لنقوم بمحوه من مسارك تماماً.",
                        color = VibrantTextMedium,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ما الذي حفز الرغبة وجعلك تسقط؟ (مثال: العزلة بالهاتف في السرير، الملل، منشور غامض):",
                        color = VibrantTextDark,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = relapseReason,
                        onValueChange = { relapseReason = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("relapse_reason_input"),
                        placeholder = { Text("المحفز أو الموقف المثير...", color = VibrantTextMedium.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VibrantTextDark,
                            unfocusedTextColor = VibrantTextDark,
                            focusedBorderColor = VibrantSOSBorder,
                            unfocusedBorderColor = VibrantBorder,
                            focusedContainerColor = VibrantBackground,
                            unfocusedContainerColor = VibrantBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "ما هي الخطة المعدلة لتجنب ذلك في المرة القادمة؟ (الدرس المستفاد):",
                        color = VibrantTextDark,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = relapseLesson,
                        onValueChange = { relapseLesson = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("relapse_lesson_input"),
                        placeholder = { Text("الدرس: سأضع الهاتف بعيداً قبل النوم، سأمارس ضغط...", color = VibrantTextMedium.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VibrantTextDark,
                            unfocusedTextColor = VibrantTextDark,
                            focusedBorderColor = VibrantSOSBorder,
                            unfocusedBorderColor = VibrantBorder,
                            focusedContainerColor = VibrantBackground,
                            unfocusedContainerColor = VibrantBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRelapseDialog = false }) {
                            Text("تراجع", color = VibrantTextMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.logRelapse(relapseReason, relapseLesson, currentDay)
                                showRelapseDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VibrantSOSBorder),
                            modifier = Modifier.testTag("save_relapse_btn")
                        ) {
                            Text("أنا بطل وسأنهض الآن!")
                        }
                    }
                }
            }
        }
    }
}

// Helper tuple class
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
data class HabitItem(val id: String, val title: String)

// --- SCREEN 2: CHAT WITH COACH ---
@Composable
fun ChatScreen(viewModel: TatbiqatiViewModel) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatLoading = viewModel.isChatLoading
    val chatError = viewModel.chatError
    var userMessageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Suggestions to quickly ask
    val suggestions = listOf(
        "كيف تؤثر العادة على هرمونات عضلاتي وقوتي؟ 💪",
        "ما حكاية دورة الدوبامين الـ 6 ساعات؟ 🔄",
        "كيف أحسن من مستواي الدراسي بعد الإقلاع؟ 📚",
        "ما هي خطة الصمود في الأسبوع الأول الحرج؟ ⚡"
    )

    // Clear and prompt if messages are empty
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isEmpty()) {
            viewModel.clearChat()
        } else {
            // Auto scroll to last message
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_container")
            .padding(16.dp)
    ) {
        // Chat Header with Clear Option
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "المدرب الذكي الشخصي 🧠⚽",
                    color = VibrantTextDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "مستشارك الرياضي والعقلي على مدار الساعة",
                    color = VibrantTextMedium,
                    fontSize = 11.sp
                )
            }
            TextButton(
                onClick = { viewModel.clearChat() },
                modifier = Modifier.testTag("clear_chat_btn")
            ) {
                Text("إعادة المحادثة 🔄", color = VibrantPrimary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal scrollable suggestions
        Text(
            text = "اقتراحات أسئلة هامة:",
            color = VibrantTextMedium,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(suggestions) { suggestion ->
                Box(
                    modifier = Modifier
                        .background(VibrantSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, VibrantBorder, RoundedCornerShape(12.dp))
                        .clickable { viewModel.sendChatMessage(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = suggestion,
                        color = VibrantTextDark,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Chat bubble list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages) { message ->
                val isUser = message.role == "user"
                val align = if (isUser) Alignment.End else Alignment.Start
                val bubbleColor = if (isUser) VibrantPrimary else VibrantSurface
                val strokeColor = if (isUser) VibrantPrimary else VibrantBorder
                val textColor = if (isUser) Color.White else VibrantTextDark

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = align
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(bubbleColor, RoundedCornerShape(16.dp))
                            .border(1.dp, strokeColor, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = textColor,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (isChatLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = VibrantPrimary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المدرب يكتب نصيحتك الآن...",
                            color = VibrantTextMedium,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            chatError?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VibrantSOSBg),
                        border = BorderStroke(1.dp, VibrantSOSBorder)
                    ) {
                        Text(
                            text = error,
                            color = VibrantSOSBorder,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Message input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = userMessageText,
                onValueChange = { userMessageText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text"),
                placeholder = { Text("اكتب سؤالك أو فضفض للمدرب هنا...", color = VibrantTextMedium.copy(alpha = 0.5f), fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VibrantTextDark,
                    unfocusedTextColor = VibrantTextDark,
                    focusedBorderColor = VibrantPrimary,
                    unfocusedBorderColor = VibrantBorder,
                    focusedContainerColor = VibrantSurface,
                    unfocusedContainerColor = VibrantSurface
                ),
                maxLines = 3
            )
            IconButton(
                onClick = {
                    if (userMessageText.isNotBlank()) {
                        viewModel.sendChatMessage(userMessageText)
                        userMessageText = ""
                    }
                },
                modifier = Modifier
                    .background(VibrantPrimary, CircleShape)
                    .size(48.dp)
                    .testTag("send_msg_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "إرسال",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}



// --- SCREEN 3: SOS EMERGENCY MODULE ---
@Composable
fun EmergencyScreen(viewModel: TatbiqatiViewModel) {
    val isEmergencyMode = viewModel.isEmergencyMode
    val timerSeconds = viewModel.emergencyTimerSeconds
    val breathingPhase = viewModel.breathingPhase
    val breathingSecsLeft = viewModel.breathingSecondsLeft
    val cycleCount = viewModel.breathingCycleCount

    // Circular scaling animation to guide breathing
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Adjust scaleFactor based on phase manually to ensure it aligns perfectly
    val finalScale = if (breathingPhase == "شهيق 💨") scaleFactor else if (breathingPhase == "زفير 🌬️") (2.1f - scaleFactor) else 1.0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("emergency_screen"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isEmergencyMode) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🚨 زر الطوارئ الفوري (SOS) 🚨",
                        color = VibrantSOSBorder,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "عندما تضرب الرغبة بقوة مباغتة، لا تنتظر!\nاضغط على الزر الأحمر بالأسفل فوراً لتفعيل بروتوكول الإنقاذ وإخضاع الرغبة وتفجير طاقة عقلك وجسدك بشكل آمن.",
                        color = VibrantTextMedium,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // Pulse Emergency Red Button
                    IconButton(
                        onClick = { viewModel.startEmergencyMode() },
                        modifier = Modifier
                            .size(160.dp)
                            .background(VibrantSOSBg, CircleShape)
                            .border(4.dp, VibrantSOSBorder, CircleShape)
                            .testTag("activate_sos_btn")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "أنقذني 🚨",
                                color = VibrantSOSBorder,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اضغط فوراً",
                                color = VibrantTextDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        } else {
            // SOS IS ACTIVE!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VibrantSOSBg),
                    border = BorderStroke(1.dp, VibrantSOSBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔴 تم تفعيل بروتوكول الإنقاذ الفوري!",
                            color = VibrantSOSBorder,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "قم الآن وغيّر مكانك أو غرفتك فوراً! الرغبة الشديدة هي تراكم عصبي للمحفزات يزول تلقائياً بعد 15 دقيقة فقط إذا غيّرت بيئتك الحالية.",
                            color = VibrantSOSText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // The 15 Minutes postponement timer card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VibrantSurface),
                    border = BorderStroke(1.dp, VibrantBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "مؤقت تأجيل الرغبة لـ 15 دقيقة ⏱️",
                            color = VibrantPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val minutes = timerSeconds / 60
                        val seconds = timerSeconds % 60
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                            color = VibrantTextDark,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "قل لنفسك: «سأنتظر 15 دقيقة فقط وأراقب الرغبة كغيمة تمر في السماء دون لمسها»",
                            color = VibrantTextMedium,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Interactive Square breathing exercise card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VibrantSurface),
                    border = BorderStroke(1.dp, VibrantPrimary)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "تمرين التنفس المربع لإيقاف ضغط الأوعية (4-4-4-4) 🧘",
                            color = VibrantTextDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Animated circle indicator
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(120.dp)
                                .scale(finalScale)
                                .background(VibrantSecondary, CircleShape)
                                .border(2.dp, VibrantPrimary, CircleShape)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = breathingPhase,
                                    color = VibrantTextDark,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$breathingSecsLeft ثوانٍ",
                                    color = VibrantPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "الدورة الحالية المكتملة: $cycleCount من أصل 5 دورات",
                            color = VibrantTextMedium,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            BreathingStepIndicator("شهيق 💨", active = breathingPhase == "شهيق 💨")
                            BreathingStepIndicator("حبس 🛑", active = breathingPhase == "حبس 🛑")
                            BreathingStepIndicator("زفير 🌬️", active = breathingPhase == "زفير 🌬️")
                            BreathingStepIndicator("حبس ✋", active = breathingPhase == "حبس ✋")
                        }
                    }
                }
            }

            // Action lists for emergency
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VibrantSurface),
                    border = BorderStroke(1.dp, VibrantBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "أفعال إنقاذ عاجلة وبديلة ⚡:",
                            color = VibrantTextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        EmergencyTip("🚿 اغسل وجهك ورأسك بماء شديد البرودة وصادم.")
                        EmergencyTip("🤸 قم بأداء 20 تمرين ضغط أو سكوات فوراً لتفريغ الدم في عضلاتك.")
                        EmergencyTip("📖 اقرأ أهدافك الكبرى: كالحصول على فورمة رياضية أحلامك أو النجاح في جامعتك.")
                        EmergencyTip("💬 راسل المدرب الذكي فوراً واكتب 'عندي رغبة' ليرشدك تكتيكياً.")
                    }
                }
            }

            // Stop/Dismiss emergency button
            item {
                Button(
                    onClick = { viewModel.stopEmergencyMode() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dismiss_sos_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "الحمد لله، تجاوزت الرغبة بنجاح وسحقت الوهم! ✅",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BreathingStepIndicator(title: String, active: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (active) VibrantPrimary else VibrantSecondary.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (active) Color.White else VibrantTextMedium,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun EmergencyTip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            color = VibrantPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = text,
            color = VibrantTextMedium,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

