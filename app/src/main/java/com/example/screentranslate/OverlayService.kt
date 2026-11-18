package com.example.screentranslate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val bgThread = HandlerThread("capture").also { it.start() }
    private val bgHandler = Handler(bgThread.looper)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var latestImage: Image? = null
    private val lock = Any()

    private var button: View? = null
    private var resultView: FrameLayout? = null

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.ARABIC)
            .build()
    )
    private var modelReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null || intent == null) return START_NOT_STICKY
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        startAsForeground()

        val code = intent.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { handler.post { stopSelf() } }
        }, handler)

        setupVirtualDisplay()
        showButton()
        prepareTranslator()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("st", "مترجم الشاشة", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "st")
            .setContentTitle("مترجم الشاشة يعمل")
            .setContentText("اضغط مطولاً على الزر العائم للإيقاف")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2979FF.toInt())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    private fun setupVirtualDisplay() {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        val reader = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r ->
            synchronized(lock) {
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                latestImage?.close()
                latestImage = img
            }
        }, bgHandler)
        imageReader = reader
        virtualDisplay = projection!!.createVirtualDisplay(
            "screen-translate", m.widthPixels, m.heightPixels, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
        )
    }

    private fun prepareTranslator() {
        toast("جاري تجهيز نموذج الترجمة...")
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { modelReady = true; toast("جاهز! اضغط زر «ترجم»") }
            .addOnFailureListener { toast("فشل تنزيل النموذج، تأكد من الإنترنت ثم أعد التشغيل") }
    }

    private fun showButton() {
        val dp = resources.displayMetrics.density
        val size = (64 * dp).toInt()
        val tv = ImageView(this).apply {
            setImageResource(R.drawable.ic_translate)
            val p = (14 * dp).toInt()
            setPadding(p, p, p, p)
            alpha = 0.95f
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2979FF.toInt(), 0xFF7C4DFF.toInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * dp).toInt(), 0xCCFFFFFF.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (200 * dp).toInt()
        }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        var moved = false; var downTime = 0L
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY
                    moved = false; downTime = SystemClock.uptimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    if (moved) { params.x = sx + dx; params.y = sy + dy; wm.updateViewLayout(tv, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (SystemClock.uptimeMillis() - downTime > 800) stopSelf() else onTap()
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(tv, params)
        button = tv
    }

    private fun onTap() {
        if (!modelReady) { toast("نموذج الترجمة لسه ما جهز"); return }
        button?.visibility = View.INVISIBLE
        handler.postDelayed({
            bgHandler.post {
                val bmp = synchronized(lock) { latestImage?.let { imageToBitmap(it) } }
                handler.post {
                    if (bmp == null) { restoreButton(); toast("تعذر التقاط الشاشة") }
                    else runOcr(bmp)
                }
            }
        }, 250)
    }

    private fun imageToBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * img.width
        val wide = Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888
        )
        wide.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(wide, 0, 0, img.width, img.height)
    }

    private fun runOcr(bmp: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text -> translateBlocks(text.textBlocks) }
            .addOnFailureListener { restoreButton(); toast("فشل قراءة النص") }
    }

    private fun translateBlocks(all: List<Text.TextBlock>) {
        val latin = Regex("[A-Za-z]{2,}")
        val blocks = all.filter { it.boundingBox != null && latin.containsMatchIn(it.text) }
        if (blocks.isEmpty()) { restoreButton(); toast("ما لقيت نص إنجليزي"); return }

        val results = arrayOfNulls<String>(blocks.size)
        var remaining = blocks.size
        blocks.forEachIndexed { i, b ->
            val src = b.text.replace("\n", " ")
            translator.translate(src)
                .addOnSuccessListener { results[i] = it }
                .addOnFailureListener { results[i] = src }
                .addOnCompleteListener {
                    remaining--
                    if (remaining == 0) showResult(blocks, results)
                }
        }
    }

    private fun showResult(blocks: List<Text.TextBlock>, results: Array<String?>) {
        val root = FrameLayout(this)
        root.setBackgroundColor(0x40000000)
        val dp = resources.displayMetrics.density
        blocks.forEachIndexed { i, b ->
            val r = b.boundingBox ?: return@forEachIndexed
            val tv = TextView(this).apply {
                text = results[i] ?: b.text
                setTextColor(Color.parseColor("#0D1B2A"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FAFFFFFF"))
                    cornerRadius = 8 * dp
                    setStroke(maxOf((1 * dp).toInt(), 1), Color.parseColor("#802979FF"))
                }
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding((3 * dp).toInt(), 0, (3 * dp).toInt(), 0)
                setAutoSizeTextTypeUniformWithConfiguration(
                    8, 22, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }
            val lp = FrameLayout.LayoutParams(
                maxOf(r.width(), (40 * dp).toInt()), maxOf(r.height(), (20 * dp).toInt())
            )
            lp.leftMargin = r.left
            lp.topMargin = r.top
            root.addView(tv, lp)
        }
        root.setOnClickListener { dismissResult() }

        val closeSize = (52 * dp).toInt()
        val close = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            val p = (14 * dp).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF0263238.toInt())
            }
            setOnClickListener { dismissResult() }
        }
        val closeLp = FrameLayout.LayoutParams(
            closeSize, closeSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = (72 * dp).toInt() }
        root.addView(close, closeLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(root, params)
        resultView = root
    }

    private fun dismissResult() {
        resultView?.let { runCatching { wm.removeView(it) } }
        resultView = null
        restoreButton()
    }

    private fun restoreButton() { button?.visibility = View.VISIBLE }

    private fun toast(s: String) = handler.post { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        resultView?.let { runCatching { wm.removeView(it) } }
        button?.let { runCatching { wm.removeView(it) } }
        synchronized(lock) { latestImage?.close(); latestImage = null }
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        translator.close()
        recognizer.close()
        bgThread.quitSafely()
        super.onDestroy()
    }
}
