package com.charles.livecaptionn.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.ui.l10n.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val onPauseResume: () -> Unit,
    private val onClose: () -> Unit,
    private val onToggleMinimize: () -> Unit,
    private val uiStrings: () -> UiStrings = { UiStrings.EMPTY }
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private var root: FrameLayout? = null
    private var statusText: TextView? = null
    private var originalContainer: LinearLayout? = null
    private var originalText: TextView? = null
    private var dividerView: View? = null
    private var translatedText: TextView? = null
    private var transcriptText: TextView? = null
    private var body: ScrollView? = null
    private var pauseButton: ImageButton? = null
    private var minButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var params: WindowManager.LayoutParams? = null
    private var header: LinearLayout? = null
    private var lastDetail: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var minimized = false
    // Minimized view is just the header, so it must stay visible.
    private val hideControlsTask = Runnable { if (!minimized) header?.visibility = View.GONE }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handleHitPx get() = dp(44)

    fun show(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        if (root != null) return
        showInternal(initialX, initialY, widthDp, heightDp)
    }

    private fun showInternal(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()

        val p = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        params = p

        // Root is a FrameLayout so we can place resize handle on top
        val frame = object : FrameLayout(context) {
            private var downX = 0f
            private var downY = 0f
            private var downOnHandle = false

            // Take over once a touch turns into a drag, so the whole caption box moves
            // (not just the header). Touches that start on the resize handle are left alone.
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX; downY = ev.rawY
                        downOnHandle = ev.x > width - handleHitPx && ev.y > height - handleHitPx
                    }
                    MotionEvent.ACTION_MOVE ->
                        if (!downOnHandle && kotlin.math.hypot(ev.rawX - downX, ev.rawY - downY) > touchSlop) return true
                }
                return false
            }
        }
        frame.setOnTouchListener(DragTouchListener())

        // Inner container (vertical layout)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#AA111111"))
            }
            background = bg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header: status + buttons (also the drag-to-move zone)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        this.header = header

        statusText = TextView(context).apply {
            text = "Status: Idle"
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        pauseButton = makeButton(android.R.drawable.ic_media_pause, uiStrings()["Pause captioning"]) { onPauseResume() }
        minButton = makeButton(android.R.drawable.arrow_down_float, uiStrings()["Minimize overlay"]) { onToggleMinimize() }
        closeButton = makeButton(android.R.drawable.ic_menu_close_clear_cancel, uiStrings()["Close overlay"]) { onClose() }

        header.addView(statusText)
        header.addView(pauseButton)
        header.addView(minButton)
        header.addView(closeButton)

        // Original speech section (shown when transcribing + translating with showOriginal)
        val origContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, dp(2))
            }
        }

        originalText = TextView(context).apply {
            setTextColor(Color.WHITE)
            text = ""
            setLineSpacing(dp(2).toFloat(), 1.05f)
            setPadding(0, dp(1), 0, dp(2))
            textSize = 14f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dividerView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        }

        origContainer.addView(originalText)
        origContainer.addView(dividerView)
        originalContainer = origContainer

        // Translated text / scrolling history (primary caption body)
        translatedText = TextView(context).apply {
            setTextColor(Color.WHITE)
            text = "…"
            setLineSpacing(dp(3).toFloat(), 1.1f)
            setPadding(0, dp(2), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        transcriptText = translatedText // alias for update() compatibility

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            addView(translatedText)
        }

        body = object : ScrollView(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent) = false
            override fun onTouchEvent(ev: MotionEvent) = false
        }.apply {
            addView(scrollContent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isFillViewport = false
            isVerticalScrollBarEnabled = true
        }

        container.addView(header)
        container.addView(origContainer)
        container.addView(body)

        frame.addView(container)

        // Resize handle at bottom-right corner: small visible grip, large touch area.
        val grip = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#88FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply { gravity = Gravity.CENTER }
        }
        val resizeHandle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(handleHitPx, handleHitPx).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            addView(grip)
            setOnTouchListener(ResizeTouchListener())
        }
        frame.addView(resizeHandle)

        root = frame
        showControls()
        try {
            wm.addView(frame, p)
        } catch (t: Throwable) {
            root = null
            params = null
            throw t
        }
    }

    fun update(ui: OverlayUiState) {
        val frame = root ?: return
        val container = frame.getChildAt(0) as? LinearLayout ?: return
        val theme = OverlayThemeCatalog.find(ui.themeId)
        val font = OverlayFontCatalog.find(ui.fontId)
        val s = uiStrings()
        // Enforce minimum 0.5 opacity for WCAG AA contrast (text on dark bg)
        val effectiveOpacity = ui.opacity.coerceIn(0.5f, 1.0f)
        (container.background as? GradientDrawable)?.setColor(
            Color.argb((effectiveOpacity * 255).roundToInt(), Color.red(theme.backgroundRgb), Color.green(theme.backgroundRgb), Color.blue(theme.backgroundRgb))
        )
        statusText?.setTextColor(theme.textRgb)
        statusText?.typeface = font.typeface
        statusText?.setTextIfChanged(buildString {
            append(s["Status"])
            append(": ")
            append(s[ui.status.displayName])
            val detail = ui.statusDetail?.trim().orEmpty()
            if (detail.isNotEmpty()) {
                append("\n")
                append(detail)
            }
        })
        minimized = ui.minimized
        val detailNow = ui.statusDetail?.trim().orEmpty()
        if (detailNow.isNotEmpty() && detailNow != lastDetail) showControls()
        lastDetail = detailNow
        pauseButton?.contentDescription = if (ui.status == RecognitionStatus.PAUSED) s["Resume captioning"] else s["Pause captioning"]
        minButton?.contentDescription = s["Minimize overlay"]
        closeButton?.contentDescription = s["Close overlay"]

        val textR = Color.red(theme.textRgb)
        val textG = Color.green(theme.textRgb)
        val textB = Color.blue(theme.textRgb)

        val display = computeOverlayTextDisplay(ui)

        if (display.showOriginal) {
            originalText?.setTextColor(Color.argb(190, textR, textG, textB))
            originalText?.typeface = Typeface.create(font.typeface, Typeface.ITALIC)
            originalText?.setTextIfChanged(display.originalText)
            originalText?.textSize = display.originalTextSizeSp
            dividerView?.setBackgroundColor(Color.argb(45, textR, textG, textB))
            originalContainer?.visibility = if (ui.minimized) View.GONE else View.VISIBLE
        } else {
            originalContainer?.visibility = View.GONE
        }

        translatedText?.setTextColor(theme.textRgb)
        translatedText?.typeface = font.typeface
        val textChanged = translatedText?.text?.toString() != display.translatedText
        translatedText?.setTextIfChanged(display.translatedText)
        translatedText?.textSize = display.translatedTextSizeSp
        translatedText?.visibility = View.VISIBLE
        body?.visibility = if (ui.minimized) View.GONE else View.VISIBLE
        pauseButton?.setImageResource(
            if (ui.status == RecognitionStatus.PAUSED) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        // Auto-scroll to bottom on new text
        if (textChanged) {
            body?.post {
                try { body?.fullScroll(View.FOCUS_DOWN) } catch (_: Throwable) { }
            }
        }
    }

    // Re-setting identical text still triggers a measure/layout pass.
    private fun TextView.setTextIfChanged(new: CharSequence) {
        if (text.toString() != new.toString()) text = new
    }

    fun hide() {
        root?.let { view ->
            try { wm.removeViewImmediate(view) } catch (_: Throwable) { }
        }
        root = null
        // Must be cleared together with root: DragTouchListener/ResizeTouchListener
        // guard on `params` being non-null before calling wm.updateViewLayout(root, ...),
        // and a touch event queued just before hide() runs would otherwise pass that
        // guard with a stale non-null params while root is already null, crashing
        // with "view must not be null" inside WindowManager.
        params = null
        handler.removeCallbacks(hideControlsTask)
        header = null
        statusText = null
        originalContainer = null
        originalText = null
        dividerView = null
        translatedText = null
        transcriptText = null
        body = null
        pauseButton = null
        minButton = null
        closeButton = null
    }

    // ── Helpers ──

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun makeButton(resId: Int, contentDesc: String, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(resId)
        contentDescription = contentDesc
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        val size = dp(32)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
        setOnClickListener { onClick() }
    }

    /** Header (status + buttons) is shown on tap and hides itself after a few seconds, like One UI. */
    private fun showControls() {
        header?.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsTask)
        handler.postDelayed(hideControlsTask, CONTROLS_HIDE_MS)
    }

    /** Keep the box fully on screen and within the allowed size range. */
    private fun clamp(lp: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
        val minH = (CaptionSettings.MIN_OVERLAY_HEIGHT_DP * density).roundToInt()
        lp.width = lp.width.coerceIn(minOf(minW, dm.widthPixels), dm.widthPixels)
        lp.height = lp.height.coerceIn(minOf(minH, dm.heightPixels), dm.heightPixels)
        lp.x = lp.x.coerceIn(0, (dm.widthPixels - lp.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (dm.heightPixels - lp.height).coerceAtLeast(0))
    }

    // ── Drag-to-move (touch anywhere on the overlay) ──

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val view = root ?: return false
                    // The frame takes over mid-gesture (no DOWN delivered), so start from here.
                    if (!dragging) {
                        dragging = true
                        startX = lp.x; startY = lp.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    lp.x = startX + (event.rawX - touchX).roundToInt()
                    lp.y = startY + (event.rawY - touchY).roundToInt()
                    clamp(lp)
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        scope.launch {
                            settingsRepository.update { it.copy(overlayX = lp.x, overlayY = lp.y) }
                        }
                    } else {
                        showControls() // a tap
                    }
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
            }
            return false
        }
    }

    // ── Resize (touch on bottom-right handle) ──

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var startW = 0; private var startH = 0
        private var touchX = 0f; private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startW = lp.width; startH = lp.height
                    touchX = event.rawX; touchY = event.rawY
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val view = root ?: return false
                    lp.width = startW + (event.rawX - touchX).roundToInt()
                    lp.height = startH + (event.rawY - touchY).roundToInt()
                    clamp(lp)
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wDp = (lp.width / density).roundToInt()
                    val hDp = (lp.height / density).roundToInt()
                    scope.launch {
                        settingsRepository.update {
                            it.copy(overlayWidthDp = wDp, overlayHeightDp = hDp)
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val CONTROLS_HIDE_MS = 4_000L
    }
}
