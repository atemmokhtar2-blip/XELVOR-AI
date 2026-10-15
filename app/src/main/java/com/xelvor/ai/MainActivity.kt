package com.xelvor.ai

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private val conversation = mutableListOf<ChatMessage>()
    private val bg = Color.rgb(12, 14, 20)
    private val panel = Color.rgb(24, 27, 37)
    private val accent = Color.rgb(137, 105, 255)
    private val textPrimary = Color.rgb(241, 242, 248)
    private val muted = Color.rgb(163, 168, 184)
    private val prefs by lazy { getSharedPreferences("xelvor_preferences", Context.MODE_PRIVATE) }

    private data class ChatMessage(val author: String, val body: String, val fromUser: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        loadConversation()
        buildUi()
        if (conversation.isEmpty()) {
            addMessage("XELVOR", "أهلًا! المرحلة الثانية تضيف حفظ المحادثات ومعاينة خطة المهمة قبل الموافقة. الذكاء الاصطناعي والتنفيذ الفعلي لم يتم ربطهما بعد.", false)
        } else renderConversation()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val branding = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        branding.addView(label("XELVOR AI", 24f, textPrimary, true))
        branding.addView(label("مساعدك الشخصي • Android", 12f, muted, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(branding, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(smallButton("السجل") { showHistoryOptions() })
        header.addView(smallButton("الإعدادات") { showSettings() }.apply {
            val p = layoutParams as LinearLayout.LayoutParams
            p.marginStart = dp(6)
            layoutParams = p
        })
        root.addView(header)

        val status = TextView(this).apply {
            text = "●  وضع آمن تجريبي  |  لا تُنفذ إجراءات تلقائيًا"
            textSize = 11f
            setTextColor(Color.rgb(203, 188, 255))
            setBackgroundColor(panel)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val statusParams = LinearLayout.LayoutParams(-1, -2)
        statusParams.topMargin = dp(14)
        root.addView(status, statusParams)

        scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, dp(14), 0, dp(12))
        }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val hint = label("اكتب طلبك، وراجع الخطة قبل الموافقة عليها", 12f, muted, false)
        hint.setPadding(0, 0, 0, dp(8))
        root.addView(hint)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            this.hint = "اكتب طلبك هنا..."
            textSize = 15f
            setTextColor(textPrimary)
            setHintTextColor(muted)
            setSingleLine(false)
            maxLines = 3
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val send = Button(this).apply {
            this.text = "إرسال"
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent))
            setOnClickListener { submitMessage() }
        }
        val sendParams = LinearLayout.LayoutParams(-2, -2)
        sendParams.marginStart = dp(8)
        composer.addView(send, sendParams)
        root.addView(composer)
        setContentView(root)
    }

    private fun submitMessage() {
        val request = input.text.toString().trim()
        if (request.isEmpty()) return
        addMessage("أنت", request, true)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        showTaskPlan(request)
    }

    private fun showTaskPlan(request: String) {
        val steps = buildPlan(request)
        val planText = buildString {
            append("طلبك:\n")
            append(request)
            append("\n\nالخطة المقترحة:\n")
            steps.forEachIndexed { index, step -> append("${index + 1}. $step\n") }
            append("\nلن يتم تنفيذ أي إجراء على الهاتف في هذه النسخة. الموافقة تسجل موافقتك على الخطة فقط.")
        }
        val content = TextView(this).apply {
            text = planText
            textSize = 15f
            setTextColor(textPrimary)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        val scrollContent = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("مراجعة خطة المهمة")
            .setView(scrollContent)
            .setPositiveButton("أوافق على الخطة", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                addMessage("XELVOR", "تم تسجيل موافقتك على الخطة. التنفيذ الفعلي والتحكم بالتطبيقات غير متاحين بعد، ولم يتم إجراء أي تغيير على هاتفك.", false)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                addMessage("XELVOR", "تم إلغاء الخطة، ولم يتم تنفيذ أي إجراء.", false)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun buildPlan(request: String): List<String> {
        val normalized = request.lowercase()
        val steps = mutableListOf<String>()
        steps += "تحديد التطبيق أو الجزء المطلوب والعمل عليه"
        if (listOf("افتح", "فتح", "شغل", "شغّل", "open", "launch").any { normalized.contains(it) }) {
            steps += "تحديد التطبيق المقصود والتحقق من اسمه"
        }
        if (listOf("ابعت", "أرسل", "رسالة", "send", "نشر", "انشر").any { normalized.contains(it) }) {
            steps += "مراجعة محتوى الرسالة أو المنشور والمستلم قبل أي إرسال"
        }
        if (listOf("احذف", "مسح", "حذف", "delete", "شراء", "ادفع", "دفع", "pay").any { normalized.contains(it) }) {
            steps += "التوقف عند الخطوة الحساسة وطلب تأكيد صريح قبل أي إجراء"
        }
        steps += "عرض النتيجة المتوقعة ومراجعتها معك قبل التنفيذ"
        return steps.distinct()
    }

    private fun showHistoryOptions() {
        AlertDialog.Builder(this)
            .setTitle("سجل المحادثات")
            .setMessage("هذه المحادثة محفوظة على هذا الجهاز.\nعدد الرسائل المحفوظة: ${conversation.size}")
            .setPositiveButton("مسح السجل") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("تأكيد مسح السجل")
                    .setMessage("هل تريد حذف سجل هذه المحادثة من هذا الجهاز؟")
                    .setNegativeButton("رجوع", null)
                    .setPositiveButton("مسح") { _, _ ->
                        conversation.clear()
                        saveConversation()
                        renderConversation()
                        addMessage("XELVOR", "تم مسح السجل المحلي وبدء محادثة جديدة.", false)
                    }
                    .show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showSettings() {
        val modes = arrayOf("سحابي (غير مربوط بعد)", "محلي على الجهاز (غير مربوط بعد)")
        val current = prefs.getInt("ai_mode", 0).coerceIn(0, 1)
        AlertDialog.Builder(this)
            .setTitle("إعدادات الذكاء الاصطناعي")
            .setSingleChoiceItems(modes, current) { dialog, which ->
                prefs.edit().putInt("ai_mode", which).apply()
                dialog.dismiss()
                val selected = if (which == 0) "السحابي" else "المحلي على الجهاز"
                AlertDialog.Builder(this)
                    .setTitle("تم حفظ التفضيل")
                    .setMessage("اختيارك الحالي: $selected. هذا تفضيل محفوظ فقط؛ الاتصال بمزود الذكاء الاصطناعي لم يتم تنفيذه بعد، ولم يتم حفظ أي مفتاح API.")
                    .setPositiveButton("حسنًا", null)
                    .show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
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
        } catch (_: Exception) {
            conversation.clear()
        }
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

    private fun smallButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 11f
            isAllCaps = false
            setTextColor(textPrimary)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
            setOnClickListener { action() }
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
