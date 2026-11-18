package com.example.screentranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val reqCapture = 1
    private val reqOverlay = 2

    private val blue = 0xFF2979FF.toInt()
    private val violet = 0xFF7C4DFF.toInt()
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun gradient(radiusDp: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, violet)).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun lp(w: Int, h: Int, topDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply { topMargin = dp(topDp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        val scroll = ScrollView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            isFillViewport = true
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(buildHeader(), lp(match, wrap))
        root.addView(buildBody(), lp(match, wrap))
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(36))
            val r = dp(32).toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(blue, violet)
            ).apply { cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_translate)
            setPadding(dp(26), dp(26), dp(26), dp(26))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(120), dp(120)))

        val title = TextView(this).apply {
            text = "مترجم الشاشة"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        header.addView(title, lp(match, wrap, 16))

        val sub = TextView(this).apply {
            text = "إنجليزي ← عربي   •   بدون إعلانات"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }
        header.addView(sub, lp(match, wrap, 6))
        return header
    }

    private fun buildBody(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFFE3E8F2.toInt())
            }
        }
        val steps = listOf(
            "اضغط «تشغيل» ووافق على الصلاحيات.",
            "يظهر زر عائم فوق أي تطبيق.",
            "اضغطه لترجمة الشاشة، واضغط أي مكان لإخفاء الترجمة.",
            "اضغط مطولاً على الزر العائم لإيقاف الخدمة."
        )
        steps.forEachIndexed { i, s -> card.addView(stepRow(i + 1, s), lp(match, wrap)) }
        body.addView(card, lp(match, wrap))

        val note = TextView(this).apply {
            text = "أول تشغيل يحتاج إنترنت لتنزيل نموذج الترجمة (مرة وحدة)، وبعدها تشتغل بدون إنترنت."
            setTextColor(0xFF37474F.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.15f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFFE8F0FE.toInt())
                cornerRadius = dp(16).toFloat()
            }
        }
        body.addView(note, lp(match, wrap, 16))

        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(30).toFloat()
        }
        val startBtn = TextView(this).apply {
            text = "تشغيل"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            compoundDrawablePadding = dp(10)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)
            background = RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), gradient(30), mask)
            isClickable = true
            setOnClickListener { startCapture() }
        }
        body.addView(startBtn, lp(match, dp(60), 20))

        val footer = TextView(this).apply {
            text = "مجاني • بدون إعلانات"
            setTextColor(0xFF90A4AE.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        body.addView(footer, lp(match, wrap, 16))
        return body
    }

    private fun stepRow(n: Int, s: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val badge = TextView(this).apply {
            text = n.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(blue, violet)
            ).apply { shape = GradientDrawable.OVAL }
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))

        val label = TextView(this).apply {
            text = s
            setTextColor(0xFF263238.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(0f, 1.15f)
            setPaddingRelative(dp(14), 0, 0, 0)
        }
        row.addView(label, LinearLayout.LayoutParams(0, wrap, 1f))
        return row
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                reqOverlay
            )
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            reqOverlay -> if (Settings.canDrawOverlays(this)) startCapture()
            reqCapture -> if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, OverlayService::class.java)
                    .putExtra("code", resultCode)
                    .putExtra("data", data)
                startForegroundService(i)
                moveTaskToBack(true)
            }
        }
    }
}
