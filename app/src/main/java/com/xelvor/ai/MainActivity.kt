package com.xelvor.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
    private val bg = Color.rgb(12, 14, 20)
    private val panel = Color.rgb(24, 27, 37)
    private val accent = Color.rgb(137, 105, 255)
    private val textPrimary = Color.rgb(241, 242, 248)
    private val muted = Color.rgb(163, 168, 184)
    private val prefs by lazy { getSharedPreferences("xelvor_preferences", Context.MODE_PRIVATE) }
    private val modelFile by lazy { File(filesDir, "xelvor-local-model.task") }
    private data class ChatMessage(val author: String, val body: String, val fromUser: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        loadConversation()
        buildUi()
        if (conversation.isEmpty()) {
            addMessage("XELVOR", "أهلًا! التطبيق أصبح مجهزًا لتشغيل نموذج لغوي محلي. من زر «النموذج» استورد ملف MediaPipe متوافق بصيغة .task أول مرة. بعد تثبيت النموذج يمكن إجراء المحادثات دون إنترنت.", false)
        } else renderConversation()
        updateStatus()
        if (modelFile.exists()) loadLocalModel(true)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val branding = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        branding.addView(label("XELVOR AI", 24f, textPrimary, true))
        branding.addView(label("مساعدك الشخصي • ذكاء محلي", 12f, muted, false))
        header.addView(branding, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(smallButton("السجل") { showHistoryOptions() })
        header.addView(smallButton("النموذج") { showSettings() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        root.addView(header)
        status = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.rgb(203, 188, 255))
            setBackgroundColor(panel)
            setPadding(dp(12), dp(10), dp(12), dp(10))
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
            hint = "اكتب رسالتك..."
            textSize = 15f
            setTextColor(textPrimary)
            setHintTextColor(muted)
            maxLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(Button(this).apply {
            text = "إرسال"
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent))
            setOnClickListener { submitMessage() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(composer)
        setContentView(root)
    }

    private fun updateStatus() {
        status.text = when {
            inference != null -> "● النموذج المحلي جاهز • المعالجة على الجهاز"
            modelFile.exists() -> "◌ ملف النموذج موجود • جارٍ تجهيزه"
            else -> "○ لم يتم تثبيت نموذج محلي • افتح «النموذج»"
        }
    }

    private fun showSettings() {
        val details = if (modelFile.exists()) "ملف النموذج موجود (" + (modelFile.length() / (1024 * 1024)) + " MB)." else "لا يوجد نموذج مثبت بعد."
        AlertDialog.Builder(this)
            .setTitle("الذكاء الاصطناعي المحلي")
            .setMessage(details + "\n\nاختر نموذج MediaPipe LLM Inference متوافقًا بصيغة .task. قد يكون حجمه مئات الميجابايت. بعد استيراده تتم معالجة الرسائل على الجهاز دون إرسالها إلى خادم.")
            .setPositiveButton("اختيار ملف النموذج") { _, _ -> pickModelFile() }
            .setNeutralButton("تحميل/إعادة تحميل") { _, _ -> loadLocalModel(false) }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun pickModelFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_MODEL)
    }

    @Deprecated("Uses the system document picker for compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        AlertDialog.Builder(this)
            .setTitle("استيراد النموذج")
            .setMessage("سيُنسخ الملف إلى مساحة التطبيق الخاصة. اختر ملف MediaPipe متوافقًا بصيغة .task وليس ملف GGUF.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("استيراد") { _, _ ->
                busy = true
                updateStatus()
                worker.execute {
                    try {
                        contentResolver.openInputStream(uri).use { source ->
                            requireNotNull(source) { "تعذر فتح الملف المحدد" }
                            FileOutputStream(modelFile).use { target -> source.copyTo(target) }
                        }
                        runOnUiThread {
                            try { inference?.close() } catch (_: Exception) {}
                            inference = null
                            busy = false
                            addMessage("XELVOR", "تم استيراد الملف. سأختبر تحميله كنموذج محلي.", false)
                            loadLocalModel(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            busy = false
                            updateStatus()
                            addMessage("XELVOR", "تعذر استيراد الملف: " + (e.message ?: "خطأ غير معروف"), false)
                        }
                    }
                }
            }.show()
    }

    private fun loadLocalModel(silent: Boolean) {
        if (!modelFile.exists()) {
            if (!silent) AlertDialog.Builder(this).setTitle("لا يوجد نموذج")
                .setMessage("استورد ملف .task متوافق أولًا. النموذج غير مضمن في APK لتجنب تضخيم حجمه.")
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
                        .setMaxTokens(512)
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
        builder.append("\nالموافقة ستطلب من النموذج المحلي توليد إجابة فقط؛ هذه النسخة لا تنفذ إجراءات تلقائية داخل التطبيقات.")
        val content = TextView(this).apply {
            text = builder.toString()
            textSize = 15f
            setTextColor(textPrimary)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        val dialog = AlertDialog.Builder(this).setTitle("مراجعة خطة المهمة")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("موافق، اسأل الذكاء الاصطناعي", null)
            .setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss(); runLocalAi(request) }
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

    private fun buildPrompt(request: String): String {
        val recent = conversation.takeLast(10).joinToString("\n") {
            (if (it.fromUser) "المستخدم: " else "XELVOR: ") + it.body
        }
        return "أنت XELVOR AI، مساعد شخصي يتحدث العربية المصرية الواضحة. أجب بدقة ووضوح ولا تخترع معلومات. إذا لم تعرف فقل ذلك، ولا تدّعِ تنفيذ إجراء على الهاتف.\n\nسياق المحادثة:\n" +
            recent + "\n\nالمستخدم: " + request + "\nXELVOR:"
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
            setBackgroundColor(if (message.fromUser) Color.rgb(48, 39, 78) else panel)
        }
        card.addView(label(message.author, 12f, if (message.fromUser) Color.rgb(208, 195, 255) else Color.rgb(179, 160, 255), true))
        card.addView(label(message.body, 15f, textPrimary, false).apply { setPadding(0, dp(5), 0, 0) })
        val params = LinearLayout.LayoutParams(
            if (message.fromUser) (resources.displayMetrics.widthPixels * 0.82f).toInt() else -1,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = if (message.fromUser) Gravity.END else Gravity.START
        params.bottomMargin = dp(10)
        messages.addView(card, params)
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
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
        setOnClickListener { action() }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_MODEL = 7301 }
}
