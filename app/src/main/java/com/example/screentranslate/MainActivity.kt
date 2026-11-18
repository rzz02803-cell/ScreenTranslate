package com.example.screentranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val reqCapture = 1
    private val reqOverlay = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }
        val info = TextView(this).apply {
            text = "مترجم الشاشة (إنجليزي ← عربي)\n\n" +
                "1) اضغط «تشغيل» ووافق على الصلاحيات.\n" +
                "2) يظهر زر عائم «ترجم» فوق أي تطبيق.\n" +
                "3) اضغطه لترجمة الشاشة، واضغط على الشاشة لإخفاء الترجمة.\n" +
                "4) اضغط مطولاً على الزر العائم لإيقاف الخدمة.\n\n" +
                "ملاحظة: أول تشغيل يحتاج إنترنت لتنزيل نموذج الترجمة (مرة وحدة)."
            textSize = 16f
            setPadding(0, 0, 0, pad)
        }
        val btn = Button(this).apply {
            text = "تشغيل"
            setOnClickListener { start() }
        }
        root.addView(info)
        root.addView(btn)
        setContentView(root)
    }

    private fun start() {
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
            reqOverlay -> if (Settings.canDrawOverlays(this)) start()
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
