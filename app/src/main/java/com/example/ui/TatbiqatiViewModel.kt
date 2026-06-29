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
                أنت المحرك والمدرب الشخصي الذكي لتطبيق "استقامة" (Estiqama). مهمتك الأساسية والوحيدة هي دعم ومساعدة الشاب العربي والرياضيين في التغلب على إدمان العادة السرية والمثيرات عبر رحلة الـ 90 يوماً واستعادة طاقتهم الجسدية والذهنية وتوجيهها نحو الرياضة والعمل والدراسة.
                
                بيانات المستخدم الحالي:
                - الاسم: ${'$'}userName
                - اليوم الحالي في التحدي: اليوم ${'$'}currentDay من أصل 90 يوماً.
                - توجه المستخدم: ${'$'}focusText
                
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
                    val history = dao.getChatMessages().takeLast(15)
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
            lowerPrompt.contains("أضرار") || lowerPrompt.contains("اضرار") || lowerPrompt.contains("مخاطر") || lowerPrompt.contains("تأثير") || lowerPrompt.contains("تلف") -> {
                """
                    ⚠️ **أضرار ومخاطر الإدمان السلوكي والافتراضي:**
                    يا بطل، الوعي بالضرر هو أول سبل النجاة. إليك التداعيات العلمية الدقيقة:
                    
                    1. **التأثير العصبي:** قصف الخلايا العصبية بفيض غير طبيعي من الدوبامين يسبب كبحاً للمستقبلات، مما يضعف شعورك بالمتعة بالأنشطة اليومية كالدراسة أو الرياضة.
                    2. **الأثر العضلي والجسدي:** استنزاف الزنك، الماغنيسيوم، وفقدان مستمر للتوافق العضلي العصبي يضعف الأداء الرياضي وعمليات الاستشفاء والنمو.
                    3. **الأثر الذهني والذاكرة:** المعاناة من ضبابية الدماغ، التشتت التام، وضعف خلايا الذاكرة قصيرة المدى وتأخير استجابة التعلم.
                    4. **الأثر النفسي والاجتماعي:** العزلة، القلق الاجتماعي المرتفع، الخوف من المواجهة، وضعف الثقة بالنفس والتقدير الذاتي.
                    
                    سلاحنا الأقوى هو الصمود، وكل يوم تمنع فيه نفسك، تبني فيه مساراً عصبياً سليماً من جديد! 💪
                """.trimIndent()
            }
            lowerPrompt.contains("نصيح") || lowerPrompt.contains("نصائح") || lowerPrompt.contains("طريق") || lowerPrompt.contains("كيف") -> {
                """
                    💡 **نصائح وتكتيكات الصمود الذهني والعملي:**
                    إليك دليلك الفوري للتطبيق للتغلب على الرغبة وصناعة الصمود:
                    
                    - **قاعدة الـ 15 دقيقة:** الرغبة الشديدة هي نبضة عصبية مؤقتة. إذا قمت بتغيير غرفتك، غسل وجهك بالماء البارد، أو أداء 20 تمرين ضغط، ستزول تماماً خلال 15 دقيقة فقط.
                    - **سد الثغرات الرقمية:** قم بتفعيل مانع المواقع والمحتوى الإباحي، وأبقِ الهاتف خارج غرفة النوم تماماً عند الذهاب للنوم.
                    - **توجيه وتسامي الطاقة:** الطاقة الفائضة لا بد أن تخرج؛ وجهها للجيم ورفع الأوزان، أو للمذاكرة المكثفة، أو تعلم مهارة برمجية أو لغوية جديدة.
                    - **قوة الجماعة وتجنب الفراغ:** املأ جدول يومك بالكامل بحيث لا تترك مكاناً للفراغ أو الملل، وتواصل مع أصدقائك الصالحين وعائلتك.
                    
                    أنت أقوى بكثير من مجرد نزوة عابرة! سر نحو هدفك ولا تلتفت! 🦾🚀
                """.trimIndent()
            }
            lowerPrompt.contains("نوم") || lowerPrompt.contains("ارق") || lowerPrompt.contains("ليل") || lowerPrompt.contains("أرق") -> {
                """
                    💤 **إرشادات النوم العميق وعلاج الأرق:**
                    يا بطل، الليل هو الوقت الأكثر حرجاً لمعظم المتعافين. إليك كيف تحمي نفسك وتنام نوماً مريحاً وعميقاً:
                    
                    1. **الهاتف خارج الغرفة:** هذا القانون غير قابل للنقاش! لا تدخل الهاتف إلى سريرك أبداً. اجعله يشحن في صالة البيت أو بعيداً عن متناول يدك.
                    2. **تخفيض الضوء والحرارة:** تبريد الغرفة وإطفاء الأنوار بالكامل يسرعان من إفراز هرمون الميلاتونين المسؤول عن الاسترخاء.
                    3. **قراءة كتاب ورقي:** اقرأ أي كتاب ورقي علمي أو ديني أو ثقافي لـ 10 دقائق لتجهيد عينيك طبيعياً وتسريع الدخول في النوم.
                    4. **تمرين التنفس المربع:** طبق التنفس (شهيق لـ 4 ثوانٍ - حبس لـ 4 - زفير لـ 4 - حبس لـ 4) لـ 5 دورات متتالية لتهدئة جهازك العصبي بالكامل.
                    
                    نومك العميق الليلة يعني استشفاء عضلي وذهني خارق لغدٍ جديد! طاب نومك يا بطل. 🌙🛌
                """.trimIndent()
            }
            lowerPrompt.contains("حكم") || lowerPrompt.contains("دين") || lowerPrompt.contains("حلال") || lowerPrompt.contains("حرام") -> {
                """
                    🕋 **البعد الروحي والديني للتعافي:**
                    يا بطل، الجانب الروحي هو أعظم محرك لقلبك وإرادتك:
                    
                    - **النية الصالحة:** تذكر دائماً أنك تعفّ نفسك وجسدك لابتغاء مرضاة الله سبحانه، وتحرص على قوة المؤمن التي يحبها الله (المؤمن القوي خير وأحب إلى الله من المؤمن الضعيف).
                    - **حفظ الجوارح:** البصر والسمع أمانتان سنسأل عنهما يوم القيامة. غض بصرك عن المثيرات الرقمية هو أول حائط صد لحماية قلبك.
                    - **تجديد التوبة المستمر:** إن وقعت، فلا تقنط أبداً من رحمة الله. توضأ وصمّم على عدم العودة، واعلم أن الله يحب التوابين ويحب المتطهرين.
                    - **الاستعانة بالصلاة والدعاء:** اجعل صلاتك في وقتها سلاحك الأقوى للنهي عن الفحشاء والمنكر، والتمس أوقات الإجابة للدعاء بالثبات والهدى وعافية الجسد والروح.
                    
                    طهر قلبك، وسل الله الثبات، واعلم أنك مأجور على كل دقيقة تجاهد فيها نفسك! 🤲🕌
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

    // --- Harms & Tips AI States ---
    var aiHarmsResult by mutableStateOf<String?>(null)
        private set
    var isHarmsLoading by mutableStateOf(false)
        private set
    var harmsError by mutableStateOf<String?>(null)
        private set

    var aiTipsResult by mutableStateOf<String?>(null)
        private set
    var isTipsLoading by mutableStateOf(false)
        private set
    var tipsError by mutableStateOf<String?>(null)
        private set

    fun generateAiHarms() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isHarmsLoading = true
                harmsError = null
            }

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
                أنت طبيب وباحث بيولوجي متميز متخصص في التعافي العصبي والجسدي من إدمان الإباحية والعادة السرية.
                قم بتحليل الأضرار العصبية والنفسية والجسدية المحددة لنمط حياة المستخدم بناءً على معلوماته.
                المستخدم الحالي:
                - الاسم الحركي: $userName
                - توجهه الرئيسي: $focusText
                - سلسلة الصمود الحالية: اليوم $currentDay
                
                اكتب تقريراً علمياً طبياً مخصصاً للغاية، بأسلوب قوي، حماسي ومبسط مليء بالتنسيق الجميل (نقاط، خط عريض، فقرات قصيرة جداً)، يوضح فيه كيف يستنزف هذا الإدمان طاقته العصبية، وما هي الأضرار البيولوجية المترتبة، وكيف سيتعافى دماغه بالكامل إذا التزم بالـ 90 يوماً.
            """.trimIndent()

            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    delay(1200)
                    val fallback = generateFallbackHarms(isAthlete, userName, currentDay)
                    withContext(Dispatchers.Main) {
                        aiHarmsResult = fallback
                    }
                } else {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(role = "user", parts = listOf(Part(text = "ولد لي تقرير الأضرار المخصص بالكامل والمفصل بناء على البيانات المذكورة.")))),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        generationConfig = GenerationConfig(temperature = 0.7f)
                    )
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: generateFallbackHarms(isAthlete, userName, currentDay)
                    withContext(Dispatchers.Main) {
                        aiHarmsResult = replyText
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    harmsError = "حدث خطأ أثناء تحميل التقرير: ${e.localizedMessage}. تم تفعيل التقرير الاحتياطي بالأسفل!"
                    aiHarmsResult = generateFallbackHarms(isAthlete, userName, currentDay)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isHarmsLoading = false
                }
            }
        }
    }

    fun generateAiTips() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isTipsLoading = true
                tipsError = null
            }

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
                أنت مدرب صمود وتنمية بشرية متخصص في التغلب على الإدمان وتوجيه الطاقة الكامنة.
                قدم نصائح عملية يومية حاسمة وقوية للمستخدم بناءً على تقدمه الحالي.
                بيانات المستخدم:
                - الاسم: $userName
                - اليوم الحالي في التحدي: اليوم $currentDay من 90 يوماً.
                - التوجه: $focusText
                
                اكتب نصيحة اليوم بأسلوب حماسي جداً، مخصص ومبسط، وقدم له "تحدياً صغيراً لليوم" (Mini-Challenge of the day) وتكتيكاً ذهنياً محدداً لسحق الرغبة فور حدوثها. نسق النص بنقاط وخط عريض وفقرات متباعدة ومريحة للعين.
            """.trimIndent()

            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    delay(1200)
                    val fallback = generateFallbackTips(isAthlete, userName, currentDay)
                    withContext(Dispatchers.Main) {
                        aiTipsResult = fallback
                    }
                } else {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(role = "user", parts = listOf(Part(text = "أعطني نصيحة الصمود اليومية المخصصة لي اليوم.")))),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        generationConfig = GenerationConfig(temperature = 0.7f)
                    )
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: generateFallbackTips(isAthlete, userName, currentDay)
                    withContext(Dispatchers.Main) {
                        aiTipsResult = replyText
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tipsError = "حدث خطأ أثناء تحميل النصيحة: ${e.localizedMessage}. تم تفعيل تكتيك الصمود الاحتياطي بالأسفل!"
                    aiTipsResult = generateFallbackTips(isAthlete, userName, currentDay)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isTipsLoading = false
                }
            }
        }
    }

    private fun generateFallbackHarms(isAthlete: Boolean, name: String, currentDay: Int): String {
        return if (isAthlete) {
            """
                ⚠️ **تقرير المخاطر والأضرار المخصص للأبطال والرياضيين (أنت يا $name):**
                
                بصفتك رياضياً يسعى لبناء كتلة عضلية ولياقة بدنية حديدية، إليك كيف تؤثر العادة السرية والإدمان الرقمي على هرموناتك وبنيتك العضلية:
                
                *   **🔋 استنزاف مستقبلات الأندروجين (Androgen Receptors):** 
                    هرمون التستوستيرون يحتاج إلى مستقبلات ليرتبط بالعضلات ويحفز الاستشفاء والضخامة العضلية. ممارسة العادة بكثافة تؤدي إلى كبح (Downregulation) هذه المستقبلات، مما يعني أنك تضيع مجهود الجيم هباءً!
                    
                *   **📉 انخفاض دافعية التدريب الشاق (الدوبامين):**
                    إغراق عقلك بجرعات دوبامين وهمية وسهلة من شاشات الهاتف يرفع من عتبة المتعة لديك. النتيجة؟ ستشعر بالكسل والخمول وتجنب التمارين الصعبة والأوزان الثقيلة لأن عقلك يرى المجهود "مملاً".
                    
                *   **🧪 توازن المعادن الأساسية (الزنك والماغنيسيوم):**
                    كل قذف يستنزف مخازن الزنك والماغنيسيوم والفسفور في جسدك. هذه المعادن هي الركيزة الأساسية لإنتاج التستوستيرون الطبيعي ومنع الشد العضلي وتحسين جودة النوم والتعافي.
                    
                *   **🧠 تشتت التركيز والتوافق العضلي العصبي (Mind-Muscle Connection):**
                    ممارسة العادة تسبب حالة من "الضباب الذهني" وتراجع التوصيل العصبي السريع، مما يضعف تركيزك وقدرتك على استهداف العضلة بشكل صحيح أثناء التكرارات الحرجة.
                    
                🌟 **أمل التعافي:** عند إتمامك الـ 90 يوماً، سيعيد دماغك تهيئة مستقبلات الأندروجين، وستشعر بقوة حقيقية وانفجار في الطاقة العضلية والذكورية داخل الجيم!
            """.trimIndent()
        } else {
            """
                ⚠️ **تقرير المخاطر والأضرار المخصص للنخبة الأكاديمية والمهنية (أنت يا $name):**
                
                بصفتك طالباً أو باحثاً تسعى للتفوق العلمي واستعادة حدة عقلك وذاكرتك، إليك كيف يدمر الإدمان خلايا تركيزك وتفوقك الدراسي:
                
                *   **🧠 تلف مستقبلات الدوبامين وضباب الدماغ (Brain Fog):**
                    المشاهدة والممارسة المتكررة تخلق "قصفاً ثنائياً" للدوبامين يفوق طاقة الدماغ. لحماية نفسه، يغلق الدماغ مستقبلات المتعة والتركيز. تشعر بحالة من التشتت الشديد، النسيان السريع، وصعوبة استيعاب المذاكرة.
                    
                *   **📉 متلازمة فقدان الشغف والدافعية الدراسية:**
                    بما أن الدماغ يحصل على أسهل وأكبر مكافأة بضغطة زر، فإن المهام التي تتطلب مجهوداً ذهنياً ووقتًا طويلاً كالقراءة وحل المسائل وحفظ الكلمات تبدو "مملة جداً" ومستحيلة، فتصاب بالتسويف المزمن (Chronic Procrastination).
                    
                *   **💤 اضطراب جودة النوم واليقظة الذهنية:**
                    الإفراز العشوائي للبرولاكتين والكورتيزول يضرب الساعة البيولوجية، فتنام نوماً غير عميق وتستيقظ متعباً فاقداً لليقظة العقلية، وتبدو كأنك لم تنم على الإطلاق.
                    
                *   **😔 ضعف الثقة بالنفس والانسحاب الاجتماعي:**
                    الصراع الداخلي والذنب المستمر يسحقان احترامك لذاتك، مما يجعلك تتجنب النقاشات الدراسية وتفضل العزلة والهروب من الواقع الدراسي بالهاتف.
                    
                🌟 **أمل التعافي:** خلال رحلة الـ 90 يوماً، يبدأ الدماغ في عملية "التلدن العصبي" (Neuroplasticity)، حيث يتخلص من الضباب، وستستعيد قدرتك على الحفظ والتركيز لعدة ساعات متواصلة وبمنتهى الشغف!
            """.trimIndent()
        }
    }

    private fun generateFallbackTips(isAthlete: Boolean, name: String, currentDay: Int): String {
        val phase = when {
            currentDay <= 7 -> "الأسبوع الأول الحرج (مرحلة الصدمة العصبية)"
            currentDay <= 21 -> "مرحلة إعادة ضبط الدوبامين وتطهير الدماغ"
            currentDay <= 45 -> "مرحلة المعركة الكبرى ومقاومة الخمول والملل"
            else -> "مرحلة ترسيخ السيادة العقلية والجسدية الكاملة"
        }
        
        val focusTip = if (isAthlete) {
            "استغل زيادة التستوستيرون الحالية لكسر وزن جديد في صالة الرياضة اليوم، ولا تجلس وحيداً بالهاتف بعد التمرين."
        } else {
            "طبق تقنية الطماطم (Pomodoro) اليوم لـ 4 جولات مذاكرة متتالية بتركيز عميق دون تشتت، وضع الهاتف خارج الغرفة."
        }

        return """
            🎯 **نصيحة الصمود اليومية المخصصة للبطل $name:**
            
            أنت الآن في **اليوم $currentDay** من التحدي. مرحلتك الحالية هي: **$phase**.
            
            🧠 **التكتيك العصبي اليومي لسحق الرغبة:**
            عندما تهاجمك رغبة مباغتة، لا تجلس وتتجادل مع عقلك. عقلك بارع في التبرير وسيخبرك "مرة واحدة فقط ولن تضر". بدلاً من ذلك، طبق تكتيك **تأخير الاستجابة (Urge Surfing)**:
            قل لنفسك: «سأنتظر 15 دقيقة فقط وأراقب الرغبة كغيمة تمر في السماء دون الاستجابة لها.»
            
            🏋️‍♂️ **توجيه الطاقة المخصص لك:**
            $focusTip
            
            🔥 **تحدي اليوم الصغير (Daily Mini-Challenge):**
            - خذ حماماً بارداً سريعاً لتنشيط دورتك الدموية وصعق خلايا الكسل.
            - قم بإلغاء متابعة أي حساب أو صفحة تثير رغبتك الرقمية فوراً.
            
            حافظ على طاقتك العظيمة يا وحش، أنت تصنع رجلاً حديدياً يفخر به الجميع! 🦾🚀
        """.trimIndent()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        breathingJob?.cancel()
    }
}
