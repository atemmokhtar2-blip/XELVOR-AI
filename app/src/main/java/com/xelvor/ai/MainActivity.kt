package com.xelvor.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.app.AlarmManager
import android.provider.AlarmClock
import android.net.Uri
import java.util.Locale
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private val conversation = mutableListOf<ChatMessage>()
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var inference: LlmInference? = null
    @Volatile private var busy = false
    @Volatile private var installingModel = false
    private val bg = Color.rgb(12, 14, 20)
    private val panel = Color.rgb(24, 27, 37)
    private val accent = Color.rgb(137, 105, 255)
    private val textPrimary = Color.rgb(241, 242, 248)
    private val muted = Color.rgb(163, 168, 184)
    private val prefs by lazy { getSharedPreferences("xelvor_preferences", Context.MODE_PRIVATE) }
    private val modelFile by lazy { File(filesDir, "xelvor-local-model.task") }
    private data class ChatMessage(val author: String, val body: String, val fromUser: Boolean)
    private data class ApprovedAction(val title: String, val explanation: String, val execute: () -> Unit)
    private val speechRequestCode = 7302

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        loadConversation()
        buildUi()
        if (conversation.isEmpty()) {
            addMessage("XELVOR", "أهلًا بك في XELVOR AI. نموذج Qwen2.5 1.5B مدمج داخل التطبيق. أول تشغيل يحتاج وقتًا ومساحة إضافية لنسخ النموذج وتهيئته، وبعدها يمكنك الدردشة دون إنترنت.", false)
        } else renderConversation()
        updateStatus()
        if (modelFile.exists()) loadLocalModel(true) else installBundledModel()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val branding = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        branding.addView(label("XELVOR AI", 27f, textPrimary, true))
        branding.addView(label("مساعدك الشخصي • ذكاء محلي", 13f, muted, false))
        header.addView(branding, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(smallButton("السجل") { showHistoryOptions() })
        header.addView(smallButton("النموذج") { showSettings() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        root.addView(header)
        status = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(203, 188, 255))
            background = roundedBackground(panel, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false; setPadding(0, dp(14), 0, dp(12)) }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val hint = label("راجع خطة المهمة قبل الموافقة. الردود من النموذج المحلي بعد تثبيته.", 12f, muted, false)
        hint.setPadding(0, 0, 0, dp(8))
        root.addView(hint)
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            this.hint = "اكتب رسالتك..."
            textSize = 15f
            setTextColor(textPrimary)
            setHintTextColor(muted)
            maxLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(panel, 16)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(Button(this).apply {
            text = "صوت"
            setTextColor(textPrimary)
            background = roundedBackground(panel, 16)
            setOnClickListener { startVoiceInput() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        composer.addView(Button(this).apply {
            text = "إرسال"
            setTextColor(Color.WHITE)
            background = roundedBackground(accent, 16)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { submitMessage() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(composer)
        setContentView(root)
    }

    private fun updateStatus() {
        status.text = when {
            inference != null -> "● Qwen2.5 1.5B جاهز • الردود على الجهاز دون إنترنت"
            installingModel -> "◌ جارٍ تجهيز نموذج الذكاء الاصطناعي داخل التطبيق..."
            busy -> "◌ XELVOR يعمل على طلبك..."
            modelFile.exists() -> "◌ النموذج موجود • جارٍ تحميله"
            else -> "◌ النموذج المحلي لم يجهز بعد"
        }
    }

    private fun showSettings() {
        val details = if (inference != null) {
            "Qwen2.5 1.5B جاهز ويعالج المحادثات على الهاتف. لا تُرسل رسائلك إلى خادم."
        } else if (modelFile.exists()) {
            "ملف النموذج موجود (" + (modelFile.length() / (1024 * 1024)) + " MB)، لكن المحرك لم يبدأ بعد."
        } else {
            "سيتم تجهيز النموذج المدمج تلقائيًا عند فتح التطبيق. يحتاج أول تشغيل إلى مساحة تخزين إضافية ووقت للنسخ."
        }
        AlertDialog.Builder(this)
            .setTitle("الذكاء الاصطناعي المحلي")
            .setMessage(details + "\n\nالنموذج: Qwen2.5 1.5B Instruct، نسخة 8-bit. حجمه يقارب 1.6 جيجابايت، وقد يحتاج نحو 2 جيجابايت أو أكثر من الذاكرة أثناء التشغيل. يلزم توفير مساحة إضافية لنسخ النموذج إلى مساحة التطبيق.")
            .setPositiveButton("تحميل / إعادة تحميل") { _, _ ->
                if (!modelFile.exists()) installBundledModel() else loadLocalModel(false)
            }
            .setNeutralButton("إعدادات إمكانية الوصول") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun installBundledModel() {
        if (busy || installingModel) return
        installingModel = true
        busy = true
        updateStatus()
        worker.execute {
            val tempFile = File(filesDir, "xelvor-local-model.task.part")
            try {
                assets.open("xelvor-local-model.task").use { source ->
                    FileOutputStream(tempFile).use { target ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                        }
                        target.fd.sync()
                    }
                }
                if (modelFile.exists()) modelFile.delete()
                check(tempFile.renameTo(modelFile)) { "تعذر تثبيت ملف النموذج في مساحة التطبيق." }
                runOnUiThread {
                    installingModel = false
                    busy = false
                    updateStatus()
                    addMessage("XELVOR", "تم تجهيز النموذج المدمج. سأبدأ تحميله على الجهاز الآن.", false)
                    loadLocalModel(true)
                }
            } catch (e: Exception) {
                tempFile.delete()
                runOnUiThread {
                    installingModel = false
                    busy = false
                    updateStatus()
                    addMessage("XELVOR", "تعذر تجهيز النموذج المدمج. تأكد من وجود مساحة تخزين كافية ثم أعد المحاولة. التفاصيل: " + (e.message ?: "غير معروفة"), false)
                }
            }
        }
    }
    private fun loadLocalModel(silent: Boolean) {
        if (!modelFile.exists()) {
            if (!silent) AlertDialog.Builder(this).setTitle("لا يوجد نموذج")
                .setMessage("ملف النموذج المدمج غير موجود في هذه النسخة. أعد تنزيل أحدث APK من GitHub Actions.")
                .setPositiveButton("حسنًا", null).show()
            updateStatus()
            return
        }
        if (busy) return
        busy = true
        updateStatus()
        if (!silent) addMessage("XELVOR", "جارٍ تجهيز النموذج على الهاتف. قد يستغرق ذلك بعض الوقت وقد يستهلك الذاكرة والبطارية.", false)
        worker.execute {
            try {
                val loaded = LlmInference.createFromOptions(
                    this,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(1280)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build()
                )
                runOnUiThread {
                    try { inference?.close() } catch (_: Exception) {}
                    inference = loaded
                    busy = false
                    updateStatus()
                    addMessage("XELVOR", "تم تحميل النموذج المحلي بنجاح. يمكنك الآن الدردشة دون إنترنت.", false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    updateStatus()
                    addMessage("XELVOR", "تعذر تحميل الملف كنموذج متوافق. تحقق من نوع الملف وإصدار النموذج. التفاصيل: " + (e.message ?: "غير متاحة"), false)
                }
            }
        }
    }

    private fun submitMessage() {
        if (busy) {
            addMessage("XELVOR", "انتظر حتى ينتهي تحميل النموذج أو الرد الحالي.", false)
            return
        }
        val request = input.text.toString().trim()
        if (request.isEmpty()) return
        addMessage("أنت", request, true)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        showTaskPlan(request)
    }

    private fun showTaskPlan(request: String) {
        val plan = buildPlan(request)
        val builder = StringBuilder("طلبك:\n").append(request).append("\n\nالخطة المقترحة:\n")
        plan.forEachIndexed { index, step -> builder.append(index + 1).append(". ").append(step).append("\n") }
        val action = detectApprovedAction(request)
        builder.append("\n\n").append(action?.explanation ?: "سيتم إرسال الطلب إلى النموذج المحلي لتوليد إجابة فقط.")
        val content = TextView(this).apply {
            text = builder.toString()
            textSize = 15f
            setTextColor(textPrimary)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        val dialog = AlertDialog.Builder(this).setTitle("مراجعة خطة المهمة")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(if (action != null) "موافق، نفّذ الإجراء" else "موافق، اسأل الذكاء الاصطناعي", null)
            .setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss(); if (action != null) { try { action.execute() } catch (e: Exception) { addMessage("XELVOR", "تعذر تنفيذ الإجراء: " + (e.message ?: "خطأ غير معروف"), false) } } else runLocalAi(request) }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                addMessage("XELVOR", "تم إلغاء الخطة، ولم يتم تنفيذ أي إجراء.", false)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun runLocalAi(request: String) {
        val engine = inference
        if (engine == null) {
            addMessage("XELVOR", "لم يتم تحميل نموذج ذكاء اصطناعي بعد. افتح «النموذج» واستورد ملف .task متوافقًا. لن أستبدل النموذج الحقيقي بردود وهمية.", false)
            return
        }
        busy = true
        updateStatus()
        addMessage("XELVOR", "جارٍ التفكير محليًا...", false)
        val placeholderIndex = conversation.lastIndex
        val prompt = buildPrompt(request)
        worker.execute {
            try {
                val answer = engine.generateResponse(prompt).trim().ifEmpty { "لم ينتج النموذج ردًا. جرّب صياغة السؤال بشكل أوضح." }
                runOnUiThread {
                    if (placeholderIndex in conversation.indices) {
                        conversation[placeholderIndex] = ChatMessage("XELVOR", answer, false)
                        saveConversation()
                        renderConversation()
                    }
                    busy = false
                    updateStatus()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    updateStatus()
                    addMessage("XELVOR", "حدث خطأ أثناء توليد الرد محليًا: " + (e.message ?: "غير معروف") + ". جرّب إعادة تحميل النموذج.", false)
                }
            }
        }
    }


    private fun detectApprovedAction(request: String): ApprovedAction? {
        val text = request.trim()
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("افتح ") || lower.startsWith("افتحلي ") || lower.startsWith("شغل ") || lower.startsWith("شغّل ")) {
            val appName = text.substringAfter(' ').trim()
            val packages = mapOf(
                "يوتيوب" to "com.google.android.youtube", "youtube" to "com.google.android.youtube",
                "واتساب" to "com.whatsapp", "whatsapp" to "com.whatsapp",
                "تيليجرام" to "org.telegram.messenger", "telegram" to "org.telegram.messenger",
                "كروم" to "com.android.chrome", "chrome" to "com.android.chrome",
                "الإعدادات" to "com.android.settings", "settings" to "com.android.settings",
                "الكاميرا" to "com.android.camera", "camera" to "com.android.camera"
            )
            val pkg = packages.entries.firstOrNull { appName.lowercase(Locale.ROOT).contains(it.key) }?.value
            if (pkg != null) return ApprovedAction("فتح التطبيق", "سيفتح XELVOR التطبيق الذي حددته: $appName. لن يرسل رسائل أو يغيّر بيانات داخله.") {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw ActivityNotFoundException("التطبيق غير مثبت أو لا يسمح النظام بفتحه")
                startActivity(launch)
                addMessage("XELVOR", "طلبت فتح $appName. تم تمرير الطلب إلى أندرويد.", false)
            }
        }
        if (lower.startsWith("ابحث عن ") || lower.startsWith("ابحثلي عن ") || lower.startsWith("search for ")) {
            val query = when {
                lower.startsWith("search for ") -> text.substring(11).trim()
                lower.startsWith("ابحثلي عن ") -> text.substringAfter("عن ").trim()
                else -> text.substringAfter("عن ").trim()
            }
            if (query.isNotBlank()) return ApprovedAction("بحث على الويب", "سيفتح البحث في المتصفح باستخدام العبارة التالية:\n$query") {
                startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
                addMessage("XELVOR", "فتحت البحث عن: $query", false)
            }
        }
        if (lower.contains("افتح الإعدادات") || lower == "settings" || lower == "افتح الاعدادات") {
            return ApprovedAction("فتح إعدادات الهاتف", "سيفتح تطبيق إعدادات أندرويد. لن يغيّر أي إعداد بنفسه.") {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                addMessage("XELVOR", "فتحت إعدادات الهاتف.", false)
            }
        }
        if (lower.startsWith("اضبط منبه") || lower.startsWith("اعمل منبه") || lower.startsWith("set an alarm")) {
            return ApprovedAction("إنشاء منبه", "سيفتح تطبيق الساعة لتحديد وقت المنبه ومراجعته قبل الحفظ.") {
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_MESSAGE, "من XELVOR AI"))
                addMessage("XELVOR", "فتحت شاشة إنشاء المنبه. راجع الوقت واحفظه داخل تطبيق الساعة.", false)
            }
        }
        if (lower.startsWith("اتصل ب") || lower.startsWith("كلم رقم") || lower.startsWith("dial ")) {
            val number = text.substringAfter(' ').trim()
            return ApprovedAction("فتح لوحة الاتصال", "سيفتح تطبيق الهاتف مع الرقم إن أمكن؛ لن يجري اتصالًا تلقائيًا.") {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                addMessage("XELVOR", "فتحت لوحة الاتصال للمراجعة. اضغط اتصال بنفسك إذا أردت.", false)
            }
        }
        if (lower == "اقرأ الشاشة" || lower == "اقرأ محتوى الشاشة" || lower == "read screen") {
            return ApprovedAction("قراءة الشاشة الحالية", "سيقرأ النص الظاهر فقط إذا فعّلت خدمة إمكانية الوصول يدويًا من إعدادات أندرويد. النص سيُمرر إلى النموذج المحلي.") {
                val screenText = com.xelvor.ai.assistant.XelvorAccessibilityService.instance?.readVisibleText()
                if (screenText.isNullOrBlank()) addMessage("XELVOR", "لم أستطع قراءة الشاشة. فعّل خدمة XELVOR من إعدادات إمكانية الوصول وافتح الشاشة التي تريد قراءتها.", false)
                else runLocalAi("حلّل النص الظاهر على شاشة الهاتف وأخبرني بما يحتويه، دون تنفيذ أي إجراء:\\n$screenText")
            }
        }
        if (lower.startsWith("اضغط على ") || lower.startsWith("اضغط ")) {
            val target = text.substringAfter("اضغط").removePrefix(" على").trim()
            if (target.isNotBlank()) return ApprovedAction("ضغط عنصر على الشاشة", "سيحاول الضغط على عنصر ظاهر اسمه «$target» فقط بعد موافقتك. تأكد أن العنصر المقصود ليس زر شراء أو إرسال أو حذف.") {
                val clicked = com.xelvor.ai.assistant.XelvorAccessibilityService.instance?.clickVisibleText(target) ?: false
                addMessage("XELVOR", if (clicked) "تم تمرير طلب الضغط على «$target» إلى أندرويد." else "لم أجد عنصرًا ظاهرًا مطابقًا لـ «$target». تأكد من تفعيل إمكانية الوصول وأن الشاشة المطلوبة مفتوحة.", false)
            }
        }
        return null
    }

    private fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث إلى XELVOR AI")
            }
            startActivityForResult(intent, speechRequestCode)
        } catch (_: ActivityNotFoundException) {
            addMessage("XELVOR", "خدمة التعرف على الصوت غير متاحة على هذا الهاتف. يمكنك الكتابة بدلًا من ذلك.", false)
        }
    }

    @Deprecated("Deprecated by Android in favor of Activity Result APIs; retained for broad compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequestCode && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                input.setSelection(input.text.length)
                submitMessage()
            }
        }
    }

    private fun buildPrompt(request: String): String {
        val history = conversation
            .dropLast(2)
            .filterNot { it.body == "جارٍ التفكير محليًا..." }
            .takeLast(8)
            .joinToString("") { message ->
                val role = if (message.fromUser) "user" else "assistant"
                "<|im_start|>" + role + "\n" + message.body + "\n<|im_end|>\n"
            }
        return "<|im_start|>system\nأنت XELVOR AI، مساعد شخصي يتحدث العربية المصرية الواضحة. أجب بدقة ووضوح ولا تخترع معلومات. إذا لم تعرف فقل ذلك. لا تدّع تنفيذ إجراءات على الهاتف.\n<|im_end|>\n" +
            history + "<|im_start|>user\n" + request + "\n<|im_end|>\n<|im_start|>assistant\n"
    }

    private fun buildPlan(request: String): List<String> {
        val normalized = request.lowercase()
        val steps = mutableListOf("فهم الطلب وتحديد النتيجة المطلوبة")
        if (listOf("افتح", "فتح", "شغل", "شغّل", "open", "launch").any { normalized.contains(it) })
            steps += "تحديد التطبيق المقصود؛ التحكم المباشر بالتطبيقات غير مفعّل بعد"
        if (listOf("ابعت", "أرسل", "رسالة", "send", "نشر", "انشر").any { normalized.contains(it) })
            steps += "مراجعة المحتوى والمستلم؛ لن يتم إرسال شيء تلقائيًا"
        if (listOf("احذف", "مسح", "حذف", "delete", "شراء", "ادفع", "دفع", "pay").any { normalized.contains(it) })
            steps += "توضيح الخطوات والمخاطر قبل أي إجراء حساس"
        steps += "إرسال السؤال إلى النموذج المحلي لتوليد الإجابة بعد موافقتك"
        return steps.distinct()
    }

    private fun showHistoryOptions() {
        AlertDialog.Builder(this).setTitle("سجل المحادثات")
            .setMessage("السجل محفوظ محليًا على الجهاز.\nعدد الرسائل: " + conversation.size)
            .setPositiveButton("مسح السجل") { _, _ ->
                AlertDialog.Builder(this).setTitle("تأكيد مسح السجل")
                    .setMessage("هل تريد حذف سجل المحادثة من هذا الجهاز؟")
                    .setNegativeButton("رجوع", null)
                    .setPositiveButton("مسح") { _, _ ->
                        conversation.clear()
                        saveConversation()
                        renderConversation()
                        addMessage("XELVOR", "تم مسح السجل المحلي.", false)
                    }.show()
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun addMessage(author: String, body: String, fromUser: Boolean) {
        val message = ChatMessage(author, body, fromUser)
        conversation.add(message)
        if (conversation.size > 200) conversation.removeAt(0)
        saveConversation()
        renderMessage(message)
    }

    private fun renderConversation() {
        messages.removeAllViews()
        conversation.forEach { renderMessage(it) }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderMessage(message: ChatMessage) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = if (message.fromUser) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(76, 54, 126), Color.rgb(45, 38, 75))).apply { cornerRadius = dp(18).toFloat() } else roundedBackground(panel, 18)
        }
        card.addView(label(message.author, 12f, if (message.fromUser) Color.rgb(208, 195, 255) else Color.rgb(179, 160, 255), true))
        card.addView(label(message.body, 15f, textPrimary, false).apply { setPadding(0, dp(5), 0, 0) })
        val params = LinearLayout.LayoutParams(
            if (message.fromUser) (resources.displayMetrics.widthPixels * 0.82f).toInt() else -1,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = if (message.fromUser) Gravity.END else Gravity.START
        params.bottomMargin = dp(12)
        messages.addView(card, params)
        card.alpha = 0f
        card.translationY = dp(8).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(300L).setInterpolator(DecelerateInterpolator()).start()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun loadConversation() {
        val raw = prefs.getString("conversation_json", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                conversation.add(ChatMessage(item.optString("author", "XELVOR"), item.optString("body", ""), item.optBoolean("fromUser", false)))
            }
        } catch (_: Exception) { conversation.clear() }
    }

    private fun saveConversation() {
        val array = JSONArray()
        conversation.forEach { message ->
            array.put(JSONObject().apply {
                put("author", message.author)
                put("body", message.body)
                put("fromUser", message.fromUser)
            })
        }
        prefs.edit().putString("conversation_json", array.toString()).apply()
    }

    private fun smallButton(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        textSize = 11f
        isAllCaps = false
        setTextColor(textPrimary)
        background = roundedBackground(panel, 14)
        setOnClickListener {
            animate().scaleX(0.96f).scaleY(0.96f).setDuration(70L).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
            }.start()
            action()
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    override fun onDestroy() {
        try { inference?.close() } catch (_: Exception) {}
        inference = null
        worker.shutdown()
        super.onDestroy()
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_MODEL = 7301 }
}
