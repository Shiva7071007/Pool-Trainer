package com.example.shichak

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.roundToInt

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var floatingToggleView: FloatingToggleView? = null
    private var floatingToggleParams: WindowManager.LayoutParams? = null
    private var toggleMarginRight = 0f
    private var toggleMarginTop = 0f
    private var notificationReceiver: OverlayNotificationReceiver? = null
    private var isOverlayVisible = false

    private var panelBounds = OverlayPanelBounds(0f, 0f, 0f, 0f)

    private val screenWidthPx: Int
        get() = resources.displayMetrics.widthPixels

    private val screenHeightPx: Int
        get() = resources.displayMetrics.heightPixels

    private val toggleButtonWidthPx: Int
        get() = (96f * resources.displayMetrics.density).toInt()

    private val toggleButtonHeightPx: Int
        get() = (40f * resources.displayMetrics.density).toInt()

    private val minPanelWidthPx: Int
        get() = (280f * resources.displayMetrics.density).toInt()

    private val minPanelHeightPx: Int
        get() = (220f * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        panelBounds = loadPanelBounds()
        createNotificationChannel()
        registerNotificationReceiver()
        startForegroundWithNotification()
        showFloatingToggle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REVEAL -> showOverlay()
            ACTION_CONCEAL -> hideOverlay()
            ACTION_TOGGLE -> toggleOverlay()
            ACTION_CONFIG_CHANGED -> overlayView?.invalidate()
            ACTION_PROFILE_CHANGED -> refreshAfterProfileChange()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshPanelLayoutFromStorage()
    }

    override fun onDestroy() {
        persistPanelBounds(sync = true)
        destroyOverlayView()
        removeFloatingToggle()
        notificationReceiver?.let { unregisterReceiver(it) }
        notificationReceiver = null
        windowManager = null
        super.onDestroy()
    }

    private fun registerNotificationReceiver() {
        notificationReceiver = OverlayNotificationReceiver()
        val filter = IntentFilter().apply {
            addAction(ACTION_DISMISS)
            addAction(ACTION_REVEAL)
            addAction(ACTION_CONCEAL)
            addAction(ACTION_TOGGLE)
            addAction(ACTION_CONFIG_CHANGED)
            addAction(ACTION_PROFILE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val showIntent = pendingBroadcast(ACTION_REVEAL, REQUEST_SHOW)
        val hideIntent = pendingBroadcast(ACTION_CONCEAL, REQUEST_HIDE)
        val exitIntent = pendingBroadcast(ACTION_DISMISS, REQUEST_EXIT)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (isOverlayVisible) {
                    getString(R.string.notification_text_visible)
                } else {
                    getString(R.string.notification_text_hidden)
                }
            )
            .setSmallIcon(R.drawable.ic_shichak)
            .setOngoing(true)

        if (isOverlayVisible) {
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_exit,
                    getString(R.string.action_conceal),
                    hideIntent
                ).build()
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_shichak,
                    getString(R.string.action_reveal),
                    showIntent
                ).build()
            )
        }

        builder.addAction(
            Notification.Action.Builder(
                R.drawable.ic_exit,
                getString(R.string.action_dismiss),
                exitIntent
            ).build()
        )

        return builder.build()
    }

    private fun pendingBroadcast(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun toggleOverlay() {
        if (isOverlayVisible) hideOverlay() else showOverlay()
    }

    private fun showFloatingToggle() {
        if (floatingToggleView != null) return

        val saved = ShichakPrefs.loadTogglePosition(this)
        if (saved != null) {
            toggleMarginRight = saved.first
            toggleMarginTop = saved.second
        } else {
            val defaults = ShichakPrefs.defaultTogglePosition(this)
            toggleMarginRight = defaults.first
            toggleMarginTop = defaults.second
        }

        val view = FloatingToggleView(
            context = this,
            isOverlayVisible = isOverlayVisible,
            onToggle = { toggleOverlay() },
            onDrag = { dx, dy -> moveFloatingToggle(dx, dy) },
            onDragFinished = { persistTogglePosition(sync = true) }
        )
        floatingToggleView = view

        val params = toggleLayoutParams()
        floatingToggleParams = params
        windowManager?.addView(view, params)
    }

    private fun toggleLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            toggleButtonWidthPx,
            toggleButtonHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = toggleMarginRight.roundToInt()
            y = toggleMarginTop.roundToInt()
        }
    }

    private fun moveFloatingToggle(dx: Float, dy: Float) {
        val view = floatingToggleView ?: return
        val params = floatingToggleParams ?: return

        toggleMarginRight = (toggleMarginRight - dx).coerceAtLeast(0f)
        toggleMarginTop = (toggleMarginTop + dy).coerceIn(
            0f,
            (screenHeightPx - toggleButtonHeightPx).toFloat()
        )
        val maxRight = (screenWidthPx - toggleButtonWidthPx).toFloat()
        toggleMarginRight = toggleMarginRight.coerceAtMost(maxRight)

        params.x = toggleMarginRight.roundToInt()
        params.y = toggleMarginTop.roundToInt()
        windowManager?.updateViewLayout(view, params)
    }

    private fun persistTogglePosition(sync: Boolean = false) {
        ShichakPrefs.saveTogglePosition(this, toggleMarginRight, toggleMarginTop, sync)
    }

    private fun reloadTogglePosition() {
        val saved = ShichakPrefs.loadTogglePosition(this)
        if (saved != null) {
            toggleMarginRight = saved.first
            toggleMarginTop = saved.second
        } else {
            val defaults = ShichakPrefs.defaultTogglePosition(this)
            toggleMarginRight = defaults.first
            toggleMarginTop = defaults.second
        }
        floatingToggleView?.let { view ->
            val params = toggleLayoutParams()
            floatingToggleParams = params
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun refreshAfterProfileChange() {
        panelBounds = loadPanelBounds()
        reloadTogglePosition()
        overlayView?.reloadFromProfile()
        overlayView?.let { view ->
            if (isOverlayVisible) {
                windowManager?.updateViewLayout(view, overlayLayoutParams())
                syncPanelOrigin(view)
            }
        }
    }

    private fun removeFloatingToggle() {
        floatingToggleView?.let { windowManager?.removeView(it) }
        floatingToggleView = null
        floatingToggleParams = null
    }

    private fun updateFloatingToggle() {
        floatingToggleView?.setOverlayVisible(isOverlayVisible)
    }

    private fun showOverlay() {
        if (isOverlayVisible) return
        panelBounds = loadPanelBounds()
        val view = ensureOverlayView()
        view.setPanelScreenOrigin(panelBounds.x, panelBounds.y)
        windowManager?.addView(view, overlayLayoutParams())
        isOverlayVisible = true
        persistPanelBounds(sync = true)
        updateFloatingToggle()
        updateNotification()
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return
        overlayView?.let { windowManager?.removeView(it) }
        isOverlayVisible = false
        persistPanelBounds(sync = true)
        updateFloatingToggle()
        updateNotification()
    }

    private fun destroyOverlayView() {
        if (isOverlayVisible) {
            overlayView?.let { windowManager?.removeView(it) }
            isOverlayVisible = false
        }
        overlayView = null
    }

    private fun ensureOverlayView(): OverlayView {
        if (overlayView == null) {
            overlayView = OverlayView(this).apply {
                onHideRequested = { hideOverlay() }
                onExitRequested = { stopSelf() }
                onPanelResize = { edge, dx, dy -> applyPanelResize(edge, dx, dy) }
                onPanelResizeFinished = { persistPanelBounds(sync = true) }
            }
        }
        return overlayView!!
    }

    private fun applyPanelResize(edge: PanelResizeEdge, dx: Float, dy: Float) {
        if (ShichakPrefs.isLayoutLocked(this)) return

        var x = panelBounds.x
        var y = panelBounds.y
        var w = panelBounds.width
        var h = panelBounds.height

        when (edge) {
            PanelResizeEdge.LEFT -> {
                x += dx
                w -= dx
            }
            PanelResizeEdge.RIGHT -> {
                w += dx
            }
            PanelResizeEdge.TOP -> {
                y += dy
                h -= dy
            }
            PanelResizeEdge.BOTTOM -> {
                h += dy
            }
        }

        panelBounds = clampPanelBounds(OverlayPanelBounds(x, y, w, h))
        persistPanelBounds()
        overlayView?.let { view ->
            windowManager?.updateViewLayout(view, overlayLayoutParams())
            syncPanelOrigin(view)
        }
    }

    private fun syncPanelOrigin(view: OverlayView) {
        view.setPanelScreenOrigin(panelBounds.x, panelBounds.y)
    }

    private fun clampPanelBounds(bounds: OverlayPanelBounds): OverlayPanelBounds {
        var x = bounds.x.coerceIn(0f, screenWidthPx.toFloat() - minPanelWidthPx)
        var y = bounds.y.coerceIn(0f, screenHeightPx.toFloat() - minPanelHeightPx)
        var w = bounds.width.coerceIn(minPanelWidthPx.toFloat(), screenWidthPx.toFloat() - x)
        var h = bounds.height.coerceIn(minPanelHeightPx.toFloat(), screenHeightPx.toFloat() - y)

        if (x + w > screenWidthPx) {
            w = screenWidthPx - x
        }
        if (y + h > screenHeightPx) {
            h = screenHeightPx - y
        }

        return OverlayPanelBounds(x, y, w, h)
    }

    private fun loadPanelBounds(): OverlayPanelBounds {
        val loaded = ShichakPrefs.loadOverlayBounds(this)
        val bounds = loaded ?: ShichakPrefs.defaultOverlayBounds(this)
        return clampPanelBounds(bounds)
    }

    private fun refreshPanelLayoutFromStorage() {
        panelBounds = loadPanelBounds()
        overlayView?.let { view ->
            if (isOverlayVisible) {
                windowManager?.updateViewLayout(view, overlayLayoutParams())
                syncPanelOrigin(view)
            }
        }
    }

    private fun persistPanelBounds(sync: Boolean = false) {
        if (panelBounds.width <= 0f || panelBounds.height <= 0f) return
        ShichakPrefs.saveOverlayBounds(this, panelBounds, sync)
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            panelBounds.width.roundToInt().coerceAtLeast(minPanelWidthPx),
            panelBounds.height.roundToInt().coerceAtLeast(minPanelHeightPx),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelBounds.x.roundToInt()
            y = panelBounds.y.roundToInt()
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.example.shichak.ACTION_DISMISS"
        const val ACTION_REVEAL = "com.example.shichak.ACTION_REVEAL"
        const val ACTION_CONCEAL = "com.example.shichak.ACTION_CONCEAL"
        const val ACTION_TOGGLE = "com.example.shichak.ACTION_TOGGLE"
        const val ACTION_CONFIG_CHANGED = "com.example.shichak.ACTION_CONFIG_CHANGED"
        const val ACTION_PROFILE_CHANGED = "com.example.shichak.ACTION_PROFILE_CHANGED"

        private const val CHANNEL_ID = "shichak_overlay"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_SHOW = 1
        private const val REQUEST_HIDE = 2
        private const val REQUEST_EXIT = 3
    }
}

enum class PanelResizeEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}
