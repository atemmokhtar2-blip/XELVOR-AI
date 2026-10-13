package com.xelvor.ai

import android.app.Activity
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

class MainActivity : Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView

    private val bg = Color.rgb(12, 14, 20)
    private val panel = Color.rgb(24, 27, 37)
    private val accent = Color.rgb(137, 105, 255)
    private val textPrimary = Color.rgb(241, 242, 248)
    private val muted = Color.rgb(163, 168, 184)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildUi()
        addMessage("XELVOR", "أهلًا! أنا مساعدك الشخصي. النسخة الحالية نموذج أولي للواجهة فقط؛ لم يتم ربط الذكاء الاصطناعي أو التحكم بالتطبيقات بعد.", false)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        header.addView(label("XELVOR AI", 25f, textPrimary, true))
        header.addView(label("مساعدك الشخصي • Android", 13f, muted, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header)

        val status = TextView(this).apply {
            text = "●  الوضع التجريبي  |  لا توجد إجراءات تلقائية"
            textSize = 12f
            setTextColor(Color.rgb(203, 188, 255))
            setBackgroundColor(panel)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, dp(14), 0, dp(12))
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val hint = label("جرّب: «رتّب لي خطة لفتح تطبيق»", 12f, muted, false)
        hint.setPadding(0, 0, 0, dp(8))
        root.addView(hint)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "اكتب طلبك هنا..."
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
        addMessage("XELVOR", "وصل طلبك. في هذه النسخة لا أرسل الطلب إلى نموذج ذكاء اصطناعي ولا أنفذ أوامر على الهاتف بعد. الخطوة التالية هي إضافة التخطيط والموافقة ثم ربط مزود الذكاء الاصطناعي بأمان.", false)
    }

    private fun addMessage(author: String, body: String, fromUser: Boolean) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            setBackgroundColor(if (fromUser) Color.rgb(48, 39, 78) else panel)
        }
        card.addView(label(author, 12f, if (fromUser) Color.rgb(208, 195, 255) else Color.rgb(179, 160, 255), true))
        card.addView(label(body, 15f, textPrimary, false).apply {
            setPadding(0, dp(5), 0, 0)
        })
        val params = LinearLayout.LayoutParams(
            if (fromUser) (resources.displayMetrics.widthPixels * 0.82f).toInt() else -1,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = if (fromUser) Gravity.END else Gravity.START
        params.bottomMargin = dp(10)
        messages.addView(card, params)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
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
