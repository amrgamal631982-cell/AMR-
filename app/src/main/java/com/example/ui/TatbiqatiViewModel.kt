package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.RelapseLog
import com.example.data.db.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TatbiqatiViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.appDao()

    // Observe DB states
    val userProfile: StateFlow<UserProfile?> = dao.getUserProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val relapseHistory: StateFlow<List<RelapseLog>> = dao.getAllRelapsesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = dao.getChatMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat loading states
    var isChatLoading by mutableStateOf(false)
        private set

    var chatError by mutableStateOf<String?>(null)

    // Emergency/SOS States
    var isEmergencyMode by mutableStateOf(false)
    var emergencyTimerSeconds by mutableStateOf(900) // 15 minutes = 900 seconds
    private var timerJob: Job? = null

    // Square breathing states (4s Inhale, 4s Hold, 4s Exhale, 4s Hold)
    var breathingPhase by mutableStateOf("شهيق 💨") // شهيق, حبس, زفير, حبس
    var breathingSecondsLeft by mutableStateOf(4)
    var breathingCycleCount by mutableStateOf(1)
    private var breathingJob: Job? = null

    init {
        // Automatically pre-populate default profile if not present
        viewModelScope.launch(Dispatchers.IO) {
            val profile = dao.getUserProfile()
            if (profile == null) {
                val newProfile = UserProfile(
                    name = "البطل",
                    challengeStartDate = System.currentTimeMillis(),
                    isAthlete = true,
                    completedHabitsCsv = "",
                    lastCheckInDate = getCurrentDateString()
                )
                dao.saveUserProfile(newProfile)
            }
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // --- Profile & Habits Management ---
    fun updateProfile(name: String, isAthlete: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.getUserProfile() ?: return@launch
            val updated = current.copy(name = name, isAthlete = isAthlete)
            dao.saveUserProfile(updated)
        }
    }

    fun toggleHabit(habitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.getUserProfile() ?: return@launch
            val today = getCurrentDateString()

            // If it's a new day, clear completed habits list first
            val baseCsv = if (current.lastCheckInDate != today) "" else current.completedHabitsCsv
            val habitsList = baseCsv.split(",").filter { it.isNotEmpty() }.toMutableList()

            if (habitsList.contains(habitId)) {
                habitsList.remove(habitId)
            } else {
                habitsList.add(habitId)
            }

            val updatedCsv = habitsList.joinToString(",")
            val updated = current.copy(
                completedHabitsCsv = updatedCsv,
                lastCheckInDate = today
            )
            dao.saveUserProfile(updated)
        }
    }

    // Reset progress on relapse
    fun logRelapse(reason: String, lessonLearned: String, currentStreak: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // Save log
            val log = RelapseLog(
                timestamp = System.currentTimeMillis(),
                reason = reason.ifBlank { "غير محدد" },
                lessonLearned = lessonLearned.ifBlank { "التزم بتجنب المثيرات فوراً وعدل خطتك" },
                streakDays = currentStreak
            )
            dao.insertRelapse(log)

            // Reset start date of challenge to now
            val current = dao.getUserProfile()
            if (current != null) {
                val updated = current.copy(
                    challengeStartDate = System.currentTimeMillis(),
                    completedHabitsCsv = "" // reset habits for today
                )
                dao.saveUserProfile(updated)
            }

            // Insert system notification in chat to encourage them
            val encourageMsg = ChatMessageEntity(
                timestamp = System.currentTimeMillis(),
                role = "model",
                text = """
                    🚨 **انتكاسة مؤقتة ولكنها درس ثمين!**
                    يا بطل، تعثرك اليوم ليس نقطة النهاية، بل هو جزء من إعادة المعايرة العصبية (Neurological Recalibration).
                    لقد حققت سلسلة من **$currentStreak يوماً** رائعاً، دماغك لم يفقد التقدم الذي أحرزه! العبرة بالاستمرار.
                    
                    **العلاقة الجسدية والعقلية:**
                    عندما تنكس، تعود بعض مستويات الدوبامين للمطالبة بالنمط القديم، لكن التزامك اليوم بالنهوض فوراً يغلق هذه الفجوة.
                    
                    **خطة التعافي الفورية:**
                    1. قم بغسل وجهك بماء بارد.
                    2. خذ نفساً عميقاً واكتب الدرس المستفاد.
                    3. ابدأ فوراً جولة جديدة! أنا معك خطوة بخطوة.
                """.trimIndent()
            )
            dao.insertChatMessage(encourageMsg)
        }
    }

    // Reset streak manually (e.g., if user wants to change startup days)
    fun setCustomStartDay(daysAgo: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.getUserProfile() ?: return@launch
            val newStartTime = System.currentTimeMillis() - (daysAgo.toLong() * 24 * 60 * 60 * 1000)
            val updated = current.copy(challengeStartDate = newStartTime)
            dao.saveUserProfile(updated)
        }
    }

    // --- Chat Engine with Gemini ---
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            // Check if emergency trigger
            val textTrimmed = text.trim()
            if (textTrimmed == "أنقذني" || textTrimmed == "عندي رغبة" || textTrimmed.lowercase() == "sos") {
                withContext(Dispatchers.Main) {
                    startEmergencyMode()
                }
                val userMsg = ChatMessageEntity(
                    timestamp = System.currentTimeMillis(),
                    role = "user",
                    text = text
                )
                dao.insertChatMessage(userMsg)
                val systemMsg = ChatMessageEntity(
                    timestamp = System.currentTimeMillis() + 10,
                    role = "model",
                    text = "🚨 **تم تفعيل بروتوكول الإنقاذ الفوري (SOS)!** لقد فتحت شاشة الإنقاذ لتوجيه طاقتك فوراً وتطبيق تمارين التنفس المربع والضغط. اتبع التعليمات الآن!"
                )
                dao.insertChatMessage(systemMsg)
                return@launch
            }

            // Insert user message in database
            val userMsg = ChatMessageEntity(
                timestamp = System.currentTimeMillis(),
                role = "user",
                text = text
            )
            dao.insertChatMessage(userMsg)

            withContext(Dispatchers.Main) {
                isChatLoading = true
                chatError = null
            }

            // Gather context
            val profile = dao.getUserProfile()
            val userName = profile?.name ?: "البطل"
            val startMillis = profile?.challengeStartDate ?: System.currentTimeMillis()
            val diffDays = ((System.currentTimeMillis() - startMillis) / (1000 * 60 * 60 * 24)).toInt() + 1
            val currentDay = if (diffDays < 1) 1 else if (diffDays > 90) 90 else diffDays
            val isAthlete = profile?.isAthlete ?: true

            val focusText = if (isAthlete) {
                "مستهدف رئيسي: رياضي / لاعب كمال أجسام / لياقة بدنية (يركز على الطاقة العضلية والتعافي وهرمون التستوستيرون والتحمل)."
            } else {
                "مستهدف رئيسي: طالب / باحث عن التفوق الأكاديمي والمهني (يركز على التركيز، الذاكرة، صفاء الذهن، وإدارة الوقت)."
            }

            val systemPrompt = """
                أنت المحرك والمدرب الشخصي الذكي لتطبيق "تطبيقاتي" (Tatbiqati). مهمتك الأساسية والوحيدة هي دعم ومساعدة الشاب العربي والرياضيين في التغلب على إدمان العادة السرية والمثيرات عبر رحلة الـ 90 يوماً واستعادة طاقتهم الجسدية والذهنية وتوجيهها نحو الرياضة والعمل والدراسة.
                
                بيانات المستخدم الحالي:
                - الاسم: $userName
                - اليوم الحالي في التحدي: اليوم $currentDay من أصل 90 يوماً.
                - توجه المستخدم: $focusText
                
                القواعد الذهبية لإجاباتك:
                1. اللغة: لغة عربية فصحى حديثة، مبسطة، قوية، رنانة، وقريبة جداً من الشباب. استخدم مصطلحات حماسية مثل (يا بطل، يا وحش، الكابتن، العقل الحديدي).
                2. النبرة: حماسية جداً، داعمة، أخوية، وعملية خالية من المواعظ الأكاديمية الجافة. تحدث كمدرب نخبة أو كأخ أكبر ملهم.
                3. التخصيص والتشبيهات الرياضية والدراسية: استخدم تشبيهات رياضية وجيم (التعافي العضلي، التستوستيرون، الأوكسجين، لياقة الصمود) والنجاح الأكاديمي (التركيز الذهني، الدوبامين الطبيعي، تفوق الامتحانات، كتابة المستقبل).
                4. الهيكل: إجاباتك يجب أن تكون سهلة القراءة للغاية. استخدم التنسيق الغامق والفقرات القصيرة والنقاط الواضحة جداً. لا تكتب أبداً فقرات طويلة متصلة (walls of text).
                5. المحتوى العلمي: اعتمد على حقائق البيولوجيا العصبية والتعافي الجسدي. مثل: دورة الدوبامين الـ 6 ساعات، وإعادة ضبط الدوبامين خلال 21 يوماً، وتأثير الإباحية على مستقبلات الدماغ (Dopamine Desensitization) مما يجعل المهام اليومية كالدراسة والتمارين مملة، وكيف أن الإقلاع يحسن التعافي العضلي، والطاقة، والتركيز.
                6. عند الانتكاس: لا تلم المستخدم مطلقاً. أخبره أن التعثر هو "عملية إعادة معايرة عصبية" وأن التقدم السابق لم يضع هباءً بل إن مسارات عصبية جديدة تم بناؤها بالفعل. شجعه فوراً للبدء ومواجهة الرغبة القادمة بخطة جديدة.
                7. في حالات الطوارئ (إذا طلبSOS أو إنقاذ أو لديه رغبة شديدة): وجهه فوراً لتغيير غرفته، غسل وجهه بماء بارد جداً، ممارسة 20 ضغط، وتطبيق التنفس المربع.
            """.trimIndent()

            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    // Fallback response for missing API Key to ensure the prototype runs perfectly
                    delay(1500)
                    val fallbackReply = generateFallbackResponse(text, currentDay, isAthlete, userName)
                    val modelMsg = ChatMessageEntity(
                        timestamp = System.currentTimeMillis(),
                        role = "model",
                        text = fallbackReply
                    )
                    dao.insertChatMessage(modelMsg)
                } else {
                    // Load conversation history from DB (limit last 15 messages for context speed)
                    val history = dao.getChatMessagesFlow().stateIn(viewModelScope).value.takeLast(15)
                    val contentsList = history.map {
                        Content(
                            role = if (it.role == "user") "user" else "model",
                            parts = listOf(Part(text = it.text))
                        )
                    }

                    val request = GenerateContentRequest(
                        contents = contentsList,
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        generationConfig = GenerationConfig(temperature = 0.7f)
                    )

                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "يا بطل، لم أتمكن من معالجة الرد حالياً. ركز على هدفك واستمر في المقاومة!"

                    val modelMsg = ChatMessageEntity(
                        timestamp = System.currentTimeMillis(),
                        role = "model",
                        text = replyText
                    )
                    dao.insertChatMessage(modelMsg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatError = "عذراً يا وحش، حدث خطأ في الاتصال بالمدرب: ${e.localizedMessage}. ركز في تمرينك وسأكون جاهزاً قريباً!"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isChatLoading = false
                }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearChatHistory()
            // Add initial greeting from coach
            val profile = dao.getUserProfile()
            val userName = profile?.name ?: "البطل"
            val initialMsg = ChatMessageEntity(
                timestamp = System.currentTimeMillis(),
                role = "model",
                text = "مرحباً بك يا $userName في ساحة التدريب والتعافي الفكري! أنا مدربك الذكي الشخصي. اسألني عن تأثير العادة على عضلاتك وتمرينك، أو كيف تزيد تركيزك الدراسي، أو شاركني بأي رغبة تواجهها لنقوم بسحقها معاً! 💪🏋️‍♂️📚"
            )
            dao.insertChatMessage(initialMsg)
        }
    }

    private fun generateFallbackResponse(userPrompt: String, day: Int, isAthlete: Boolean, name: String): String {
        val lowerPrompt = userPrompt.lowercase()
        return when {
            lowerPrompt.contains("انتكست") || lowerPrompt.contains("فشلت") || lowerPrompt.contains("عدت") -> {
                """
                    🚨 **يا بطل، قف على قدميك فوراً!**
                    رأيتك تسقط، والآن أريد أن أراك تنهض بقوة أكبر. الانتكاسة ليست نهاية العالم، دماغك أجرى **عملية إعادة معايرة عصبية** طوال فترة صمودك السابقة. المسارات العصبية الجديدة التي بنتها إرادتك لم تختفِ بزلّة واحدة!
                    
                    **لماذا حدث هذا؟**
                    لنحلل الأمر كرياضيين محترفين: ما هو المحفز الذي قادك للسقوط؟ هل كان الملل؟ العزلة والهاتف؟ التعب الجسدي الشديد؟
                    
                    **خطة العمل الفورية:**
                    1. اغسل وجهك بالماء المثلج فوراً لتصدم جهازك العصبي وتنزل مستويات الرغبة.
                    2. قم بأداء 20 تمرين ضغط لإفراغ طاقة الدم المحتبسة في العضلات.
                    3. قم بتعديل خطتك اليومية وتجنب العزلة بالهاتف لأكثر من 30 دقيقة.
                    
                    أنا معك ولن أتركك. هيا بنا نفتتح جولة جديدة، واليوم الأول يبدأ الآن بكامل قوتنا! 🦾🔥
                """.trimIndent()
            }
            lowerPrompt.contains("عضل") || lowerPrompt.contains("رياض") || lowerPrompt.contains("تست") || lowerPrompt.contains("هرمون") -> {
                """
                    🏋️‍♂️ **التعافي البيولوجي وبناء العضلات:**
                    يا بطل الجيم، إليك الحقيقة العلمية التي ستجعلك متمسكاً بالتعافي بكل جوارحك:
                    
                    **1. مستويات الأندروجين والمستقبلات:**
                    العادة السرية والإدمان يستنزفان مستقبلات الأندروجين في دماغك وعضلاتك، مما يضعف كفاءة استهلاك التستوستيرون الحر لبناء الألياف العضلية. عندما تقلع، تستعيد هذه المستقبلات فعاليتها، مما يسرع عملية الضخامة والاستشفاء العضلي!
                    
                    **2. مستويات التستوستيرون والأكسجين:**
                    خلال فترة الإقلاع (خاصة حول اليوم السابع)، تظهر الأبحاث زيادة واضحة في مستويات التستوستيرون. الأهم من ذلك هو توفير الزنك والماغنيسيوم وفيتامينات ب التي تستنزف مع القذف المتكرر.
                    
                    **3. مستويات الطاقة والتحفيز (الدوبامين):**
                    بإيقاف التدفق الوهمي والسهل للدوبامين، تصبح التمارين الشاقة في الجيم هي المصدر الطبيعي واللذيذ للدوبامين. ستلاحظ طاقة هائلة، رغبة جارفة لرفع أوزان أثقل، وصفاء ذهني يزيد من دافعيتك.
                    
                    دع الطاقة الفائضة تنفجر في الأوزان وليس في شاشات الهاتف! 💪🔥
                """.trimIndent()
            }
            lowerPrompt.contains("دراس") || lowerPrompt.contains("تركيز") || lowerPrompt.contains("حفظ") || lowerPrompt.contains("عقل") -> {
                """
                    📚 **العقل الحديدي والتفوق الدراسي:**
                    أهلاً بك يا بطل العلم والمستقبل. إليك كيف يعيد الإقلاع بناء قواك العقلية والذهنية:
                    
                    **1. علاج ضباب الدماغ (Brain Fog):**
                    ممارسة العادة السرية بكثافة تغرق الدماغ بالبرولاكتين والدوبامين المؤقت، مما يخلق حالة من الخمول والضبابية الذهنية وصعوبة في الحفظ واستدعاء المعلومات. بعد الإقلاع، ستلاحظ تحسناً حاداً في صفاء الذهن والقدرة الحفظية.
                    
                    **2. استعادة حساسية الدوبامين:**
                    عندما يتلقى الدماغ مستويات هائلة وغير طبيعية من الدوبامين بسبب المثيرات، فإنه يغلق بعض المستقبلات لحماية نفسه. هذا يجعل المذاكرة وحل المسائل الصعبة تبدو "مملة ومستحيلة". بالتعافي، يعود الدماغ لحساسيته الطبيعية وتصبح المذاكرة ممتعة ومجزية.
                    
                    **3. الهدوء العقلي والتركيز الطويل:**
                    ستتخلص من التشتت والبحث المستمر عن جرعة الدوبامين التالية. سيمكنك الجلوس على مكتبك لـ 50 دقيقة متواصلة بتركيز كامل، مما يحسن درجاتك وفهمك للامتحانات.
                    
                    وجه هذه الطاقة الذهنية العظيمة لصناعة مستقبلك الذي تفخر به! 💻🔬
                """.trimIndent()
            }
            else -> {
                """
                    🔥 **مرحباً بك يا وحش في اليوم $day من رحلة البطولة والتعافي!**
                    
                    أنا فخور جداً بوجودك هنا وبمقاومتك المستمرة. رحلتك هي ترويض حقيقي للذات وبناء لقوة الإرادة التي لا تُقهر.
                    
                    **نصيحة الكابتن لليوم:**
                    بما أنك في **اليوم $day**، فإن دماغك يمر حالياً بمرحلة إعادة تهيئة حقيقية لمسارات المتعة الطبيعية. تذكر أن رغبة الـ 6 ساعات هي روتين عصبي عابر ومؤقت، يستمر لـ 15 دقيقة فقط ثم يتلاشى تماماً إذا قمت بتغيير مكانك أو مارست تمرين ضغط سريع.
                    
                    هل تواجه تحديات معينة اليوم في تمرينك أو دراستك؟ أخبرني بها وسنصنع لها خطة هجومية معاً! 🦾⚽
                """.trimIndent()
            }
        }
    }

    // --- Emergency / SOS Protocol Mode ---
    fun startEmergencyMode() {
        isEmergencyMode = true
        emergencyTimerSeconds = 900 // 15 mins
        breathingCycleCount = 1
        breathingPhase = "شهيق 💨"
        breathingSecondsLeft = 4

        // Start 15 mins timer
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (emergencyTimerSeconds > 0 && isEmergencyMode) {
                delay(1000)
                emergencyTimerSeconds--
            }
            if (emergencyTimerSeconds <= 0) {
                isEmergencyMode = false
            }
        }

        // Start square breathing helper
        breathingJob?.cancel()
        breathingJob = viewModelScope.launch {
            while (isEmergencyMode) {
                delay(1000)
                breathingSecondsLeft--
                if (breathingSecondsLeft <= 0) {
                    breathingSecondsLeft = 4
                    when (breathingPhase) {
                        "شهيق 💨" -> {
                            breathingPhase = "حبس 🛑"
                        }
                        "حبس 🛑" -> {
                            breathingPhase = "زفير 🌬️"
                        }
                        "زفير 🌬️" -> {
                            breathingPhase = "حبس ✋"
                        }
                        "حبس ✋" -> {
                            breathingPhase = "شهيق 💨"
                            breathingCycleCount++
                        }
                    }
                }
            }
        }
    }

    fun stopEmergencyMode() {
        isEmergencyMode = false
        timerJob?.cancel()
        breathingJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        breathingJob?.cancel()
    }
}
