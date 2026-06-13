package com.example.shichak

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF

data class OverlayPanelBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class NormalizedTableRect(
    val marginLeft: Float,
    val marginTop: Float,
    val marginRight: Float,
    val marginBottom: Float
) {
    fun toRect(panelW: Float, panelH: Float): RectF {
        if (panelW <= 0f || panelH <= 0f) return RectF()
        val left = marginLeft.coerceIn(0f, 1f) * panelW
        val top = marginTop.coerceIn(0f, 1f) * panelH
        val right = panelW - marginRight.coerceIn(0f, 1f) * panelW
        val bottom = panelH - marginBottom.coerceIn(0f, 1f) * panelH
        return RectF(left, top, maxOf(right, left), maxOf(bottom, top))
    }

    companion object {
        fun fromRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            panelW: Float,
            panelH: Float
        ): NormalizedTableRect {
            if (panelW <= 0f || panelH <= 0f) {
                return NormalizedTableRect(0.15f, 0.25f, 0.15f, 0.25f)
            }
            return NormalizedTableRect(
                marginLeft = (left / panelW).coerceIn(0f, 1f),
                marginTop = (top / panelH).coerceIn(0f, 1f),
                marginRight = ((panelW - right) / panelW).coerceIn(0f, 1f),
                marginBottom = ((panelH - bottom) / panelH).coerceIn(0f, 1f)
            )
        }
    }
}

data class ProfileSnapshot(
    val overlayMargins: ScreenMargins?,
    val tableNormalized: NormalizedTableRect?,
    val rectLocked: Boolean,
    val aimMargins: Pair<ScreenMargins, ScreenMargins>?,
    val toggleMarginRight: Float?,
    val toggleMarginTop: Float?,
    val redDragPercent: Int,
    val greenDragPercent: Int,
    val landscapeSideMarginPercent: Int,
    val aimLineAlphaPercent: Int,
    val reboundAlphaPercent: Int,
    val reboundCount: Int,
    val lineThicknessLevel: Int,
    val angleHudEnabled: Boolean,
    val aimDragOffsetEnabled: Boolean
)

object ShichakPrefs {
    const val PREFS_NAME = "shichak_prefs"
    const val DEFAULT_PROFILE_ID = "default"

    const val KEY_ACTIVE_PROFILE = "pref_active_profile"
    const val KEY_CUSTOM_PROFILES = "pref_custom_profiles"

    const val KEY_RED_DRAG_PERCENT = "pref_red_drag_percent"
    const val KEY_GREEN_DRAG_PERCENT = "pref_green_drag_percent"
    const val KEY_OVERLAY_ML = "pref_overlay_margin_l"
    const val KEY_OVERLAY_MT = "pref_overlay_margin_t"
    const val KEY_OVERLAY_MR = "pref_overlay_margin_r"
    const val KEY_OVERLAY_MB = "pref_overlay_margin_b"
    const val KEY_RECT_ML = "pref_rect_margin_l"
    const val KEY_RECT_MT = "pref_rect_margin_t"
    const val KEY_RECT_MR = "pref_rect_margin_r"
    const val KEY_RECT_MB = "pref_rect_margin_b"
    const val KEY_RECT_NL = "pref_rect_norm_l"
    const val KEY_RECT_NT = "pref_rect_norm_t"
    const val KEY_RECT_NR = "pref_rect_norm_r"
    const val KEY_RECT_NB = "pref_rect_norm_b"
    const val KEY_RECT_LOCKED = "pref_rect_locked"
    const val KEY_AIM1_ML = "pref_aim1_margin_l"
    const val KEY_AIM1_MT = "pref_aim1_margin_t"
    const val KEY_AIM2_ML = "pref_aim2_margin_l"
    const val KEY_AIM2_MT = "pref_aim2_margin_t"
    const val KEY_LANDSCAPE_SIDE_MARGIN_PERCENT = "pref_landscape_side_margin_percent"
    const val KEY_AIM_LINE_ALPHA_PERCENT = "pref_aim_line_alpha_percent"
    const val KEY_REBOUND_ALPHA_PERCENT = "pref_rebound_alpha_percent"
    const val KEY_REBOUND_COUNT = "pref_rebound_count"
    const val KEY_LINE_THICKNESS_LEVEL = "pref_line_thickness_level"
    const val KEY_ANGLE_HUD_ENABLED = "pref_angle_hud_enabled"
    const val KEY_AIM_DRAG_OFFSET_ENABLED = "pref_aim_drag_offset_enabled"
    const val KEY_TOGGLE_MR = "pref_toggle_margin_r"
    const val KEY_TOGGLE_MT = "pref_toggle_margin_t"

    const val DEFAULT_RED_DRAG_PERCENT = 45
    const val DEFAULT_GREEN_DRAG_PERCENT = 95
    const val DEFAULT_LANDSCAPE_SIDE_MARGIN_PERCENT = 12
    const val DEFAULT_AIM_LINE_ALPHA_PERCENT = 75
    const val DEFAULT_REBOUND_ALPHA_PERCENT = 70
    const val DEFAULT_REBOUND_COUNT = 3
    const val DEFAULT_LINE_THICKNESS_LEVEL = 3
    const val MIN_LINE_ALPHA_PERCENT = 10
    const val MAX_LINE_ALPHA_PERCENT = 100
    const val MIN_REBOUND_COUNT = 0
    const val MAX_REBOUND_COUNT = 5
    const val MIN_LINE_THICKNESS_LEVEL = 1
    const val MAX_LINE_THICKNESS_LEVEL = 5

    private val LINE_THICKNESS_DP = floatArrayOf(2f, 3f, 4f, 5f, 6f)

    fun activeProfileId(context: Context): String {
        return prefs(context).getString(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID
    }

    fun activeProfileDisplayName(context: Context): String {
        val id = activeProfileId(context)
        return if (id == DEFAULT_PROFILE_ID) {
            context.getString(R.string.profile_default_name)
        } else {
            id
        }
    }

    fun customProfileNames(context: Context): List<String> {
        return prefs(context).getStringSet(KEY_CUSTOM_PROFILES, emptySet())?.sorted() ?: emptyList()
    }

    fun allProfileIds(context: Context): List<String> {
        return listOf(DEFAULT_PROFILE_ID) + customProfileNames(context)
    }

    fun switchProfile(context: Context, profileId: String) {
        prefs(context).edit().putString(KEY_ACTIVE_PROFILE, profileId).commit()
    }

    fun createProfile(context: Context, name: String): Boolean {
        val sanitized = name.trim()
        if (sanitized.isEmpty() || sanitized == DEFAULT_PROFILE_ID) return false
        if (customProfileNames(context).contains(sanitized)) return false

        val snapshot = captureSnapshot(context)
        val updated = customProfileNames(context).toMutableSet().apply { add(sanitized) }
        prefs(context).edit().putStringSet(KEY_CUSTOM_PROFILES, updated).commit()
        switchProfile(context, sanitized)
        applySnapshot(context, snapshot)
        return true
    }

    fun deleteProfile(context: Context, profileId: String): Boolean {
        if (profileId == DEFAULT_PROFILE_ID) return false
        if (!customProfileNames(context).contains(profileId)) return false

        val p = prefs(context)
        val editor = p.edit()
        val prefix = profileKeyPrefix(profileId)
        p.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        val updated = customProfileNames(context).toMutableSet().apply { remove(profileId) }
        editor.putStringSet(KEY_CUSTOM_PROFILES, updated)
        if (activeProfileId(context) == profileId) {
            editor.putString(KEY_ACTIVE_PROFILE, DEFAULT_PROFILE_ID)
        }
        editor.commit()
        return true
    }

    fun captureSnapshot(context: Context): ProfileSnapshot {
        return ProfileSnapshot(
            overlayMargins = loadOverlayMargins(context),
            tableNormalized = loadNormalizedTableRect(context),
            rectLocked = isLayoutLocked(context),
            aimMargins = loadAimMargins(context),
            toggleMarginRight = loadToggleMarginRight(context),
            toggleMarginTop = loadToggleMarginTop(context),
            redDragPercent = redDragPercent(context),
            greenDragPercent = greenDragPercent(context),
            landscapeSideMarginPercent = landscapeSideMarginPercent(context),
            aimLineAlphaPercent = aimLineAlphaPercent(context),
            reboundAlphaPercent = reboundAlphaPercent(context),
            reboundCount = reboundCount(context),
            lineThicknessLevel = lineThicknessLevel(context),
            angleHudEnabled = isAngleHudEnabled(context),
            aimDragOffsetEnabled = isAimDragOffsetEnabled(context)
        )
    }

    fun applySnapshot(context: Context, snapshot: ProfileSnapshot) {
        snapshot.overlayMargins?.let { saveOverlayMargins(context, it, sync = true) }
        if (snapshot.tableNormalized != null) {
            saveNormalizedTableRect(context, snapshot.tableNormalized, snapshot.rectLocked, sync = true)
        }
        snapshot.aimMargins?.let { (a, b) -> saveAimMargins(context, a, b, sync = true) }
        if (snapshot.toggleMarginRight != null && snapshot.toggleMarginTop != null) {
            saveTogglePosition(context, snapshot.toggleMarginRight, snapshot.toggleMarginTop, sync = true)
        }
        saveRedDragPercent(context, snapshot.redDragPercent)
        saveGreenDragPercent(context, snapshot.greenDragPercent)
        saveLandscapeSideMarginPercent(context, snapshot.landscapeSideMarginPercent)
        saveAimLineAlphaPercent(context, snapshot.aimLineAlphaPercent)
        saveReboundAlphaPercent(context, snapshot.reboundAlphaPercent)
        saveReboundCount(context, snapshot.reboundCount)
        saveLineThicknessLevel(context, snapshot.lineThicknessLevel)
        saveAngleHudEnabled(context, snapshot.angleHudEnabled)
        saveAimDragOffsetEnabled(context, snapshot.aimDragOffsetEnabled)
    }

    fun lineThicknessPx(context: Context): Float {
        val level = lineThicknessLevel(context).coerceIn(MIN_LINE_THICKNESS_LEVEL, MAX_LINE_THICKNESS_LEVEL)
        val dp = LINE_THICKNESS_DP[level - 1]
        return dp * context.resources.displayMetrics.density
    }

    fun redDragPercent(context: Context): Int {
        return getInt(context, KEY_RED_DRAG_PERCENT, DEFAULT_RED_DRAG_PERCENT)
    }

    fun greenDragPercent(context: Context): Int {
        return getInt(context, KEY_GREEN_DRAG_PERCENT, DEFAULT_GREEN_DRAG_PERCENT)
    }

    fun redDragFactor(context: Context): Float = redDragPercent(context) / 100f

    fun greenDragFactor(context: Context): Float = greenDragPercent(context) / 100f

    fun landscapeSideMarginPercent(context: Context): Int {
        return getInt(context, KEY_LANDSCAPE_SIDE_MARGIN_PERCENT, DEFAULT_LANDSCAPE_SIDE_MARGIN_PERCENT)
    }

    fun saveRedDragPercent(context: Context, percent: Int) {
        putInt(context, KEY_RED_DRAG_PERCENT, percent.coerceIn(1, 100))
    }

    fun saveGreenDragPercent(context: Context, percent: Int) {
        putInt(context, KEY_GREEN_DRAG_PERCENT, percent.coerceIn(1, 100))
    }

    fun saveLandscapeSideMarginPercent(context: Context, percent: Int) {
        putInt(context, KEY_LANDSCAPE_SIDE_MARGIN_PERCENT, percent.coerceIn(5, 40))
    }

    fun aimLineAlphaPercent(context: Context): Int {
        return getInt(context, KEY_AIM_LINE_ALPHA_PERCENT, DEFAULT_AIM_LINE_ALPHA_PERCENT)
    }

    fun reboundAlphaPercent(context: Context): Int {
        return getInt(context, KEY_REBOUND_ALPHA_PERCENT, DEFAULT_REBOUND_ALPHA_PERCENT)
    }

    fun aimLineAlpha(context: Context): Int = alphaByteFromPercent(aimLineAlphaPercent(context))

    fun reboundAlpha(context: Context): Int = alphaByteFromPercent(reboundAlphaPercent(context))

    fun saveAimLineAlphaPercent(context: Context, percent: Int) {
        putInt(
            context,
            KEY_AIM_LINE_ALPHA_PERCENT,
            percent.coerceIn(MIN_LINE_ALPHA_PERCENT, MAX_LINE_ALPHA_PERCENT)
        )
    }

    fun saveReboundAlphaPercent(context: Context, percent: Int) {
        putInt(
            context,
            KEY_REBOUND_ALPHA_PERCENT,
            percent.coerceIn(MIN_LINE_ALPHA_PERCENT, MAX_LINE_ALPHA_PERCENT)
        )
    }

    fun reboundCount(context: Context): Int {
        return getInt(context, KEY_REBOUND_COUNT, DEFAULT_REBOUND_COUNT)
            .coerceIn(MIN_REBOUND_COUNT, MAX_REBOUND_COUNT)
    }

    fun saveReboundCount(context: Context, count: Int) {
        putInt(context, KEY_REBOUND_COUNT, count.coerceIn(MIN_REBOUND_COUNT, MAX_REBOUND_COUNT))
    }

    fun lineThicknessLevel(context: Context): Int {
        return getInt(context, KEY_LINE_THICKNESS_LEVEL, DEFAULT_LINE_THICKNESS_LEVEL)
            .coerceIn(MIN_LINE_THICKNESS_LEVEL, MAX_LINE_THICKNESS_LEVEL)
    }

    fun saveLineThicknessLevel(context: Context, level: Int) {
        putInt(
            context,
            KEY_LINE_THICKNESS_LEVEL,
            level.coerceIn(MIN_LINE_THICKNESS_LEVEL, MAX_LINE_THICKNESS_LEVEL)
        )
    }

    fun isAngleHudEnabled(context: Context): Boolean {
        return getBoolean(context, KEY_ANGLE_HUD_ENABLED, false)
    }

    fun saveAngleHudEnabled(context: Context, enabled: Boolean) {
        putBoolean(context, KEY_ANGLE_HUD_ENABLED, enabled)
    }

    fun isAimDragOffsetEnabled(context: Context): Boolean {
        return getBoolean(context, KEY_AIM_DRAG_OFFSET_ENABLED, true)
    }

    fun saveAimDragOffsetEnabled(context: Context, enabled: Boolean) {
        putBoolean(context, KEY_AIM_DRAG_OFFSET_ENABLED, enabled)
    }

    private fun alphaByteFromPercent(percent: Int): Int {
        return (255f * percent.coerceIn(MIN_LINE_ALPHA_PERCENT, MAX_LINE_ALPHA_PERCENT) / 100f)
            .toInt()
            .coerceIn(0, 255)
    }

    fun hasSavedOverlayBounds(context: Context): Boolean {
        return prefs(context).contains(scopedKey(context, KEY_OVERLAY_ML))
    }

    fun loadOverlayBounds(context: Context): OverlayPanelBounds? {
        val margins = loadOverlayMargins(context) ?: return null
        val (screenW, screenH) = ScreenLayout.screenSize(context)
        if (screenW <= 0 || screenH <= 0) return null
        return ScreenLayout.marginsToBounds(margins, screenW, screenH)
    }

    fun saveOverlayBounds(context: Context, bounds: OverlayPanelBounds, sync: Boolean = false) {
        val (screenW, screenH) = ScreenLayout.screenSize(context)
        if (screenW <= 0 || screenH <= 0 || bounds.width <= 0f || bounds.height <= 0f) return
        saveOverlayMargins(context, ScreenLayout.boundsToMargins(bounds, screenW, screenH), sync)
    }

    fun loadTogglePosition(context: Context): Pair<Float, Float>? {
        val mr = loadToggleMarginRight(context) ?: return null
        val mt = loadToggleMarginTop(context) ?: return null
        return mr to mt
    }

    fun saveTogglePosition(context: Context, marginRight: Float, marginTop: Float, sync: Boolean = false) {
        val editor = prefs(context).edit()
            .putFloat(scopedKey(context, KEY_TOGGLE_MR), marginRight.coerceAtLeast(0f))
            .putFloat(scopedKey(context, KEY_TOGGLE_MT), marginTop.coerceAtLeast(0f))
        if (sync) editor.commit() else editor.apply()
    }

    private fun loadToggleMarginRight(context: Context): Float? {
        val key = scopedKey(context, KEY_TOGGLE_MR)
        if (!prefs(context).contains(key)) return null
        return prefs(context).getFloat(key, 0f)
    }

    private fun loadToggleMarginTop(context: Context): Float? {
        val key = scopedKey(context, KEY_TOGGLE_MT)
        if (!prefs(context).contains(key)) return null
        return prefs(context).getFloat(key, 0f)
    }

    private fun loadOverlayMargins(context: Context): ScreenMargins? {
        val p = prefs(context)
        val mlKey = scopedKey(context, KEY_OVERLAY_ML)
        if (!p.contains(mlKey)) return null
        return ScreenMargins(
            p.getFloat(scopedKey(context, KEY_OVERLAY_ML), 0f),
            p.getFloat(scopedKey(context, KEY_OVERLAY_MT), 0f),
            p.getFloat(scopedKey(context, KEY_OVERLAY_MR), 0f),
            p.getFloat(scopedKey(context, KEY_OVERLAY_MB), 0f)
        )
    }

    private fun saveOverlayMargins(context: Context, margins: ScreenMargins, sync: Boolean) {
        val editor = prefs(context).edit()
            .putFloat(scopedKey(context, KEY_OVERLAY_ML), margins.left)
            .putFloat(scopedKey(context, KEY_OVERLAY_MT), margins.top)
            .putFloat(scopedKey(context, KEY_OVERLAY_MR), margins.right)
            .putFloat(scopedKey(context, KEY_OVERLAY_MB), margins.bottom)
        if (sync) editor.commit() else editor.apply()
    }

    fun loadNormalizedTableRect(context: Context): NormalizedTableRect? {
        val p = prefs(context)
        val nlKey = scopedKey(context, KEY_RECT_NL)
        if (p.contains(nlKey)) {
            return NormalizedTableRect(
                p.getFloat(nlKey, 0f),
                p.getFloat(scopedKey(context, KEY_RECT_NT), 0f),
                p.getFloat(scopedKey(context, KEY_RECT_NR), 0f),
                p.getFloat(scopedKey(context, KEY_RECT_NB), 0f)
            )
        }
        return null
    }

    fun saveNormalizedTableRect(
        context: Context,
        normalized: NormalizedTableRect,
        locked: Boolean,
        sync: Boolean = false
    ) {
        val editor = prefs(context).edit()
            .putFloat(scopedKey(context, KEY_RECT_NL), normalized.marginLeft)
            .putFloat(scopedKey(context, KEY_RECT_NT), normalized.marginTop)
            .putFloat(scopedKey(context, KEY_RECT_NR), normalized.marginRight)
            .putFloat(scopedKey(context, KEY_RECT_NB), normalized.marginBottom)
            .putBoolean(scopedKey(context, KEY_RECT_LOCKED), locked)
        if (sync) editor.commit() else editor.apply()
    }

    /** @deprecated Screen-anchored table margins; kept for one-time migration only. */
    fun loadLegacyTableMargins(context: Context): ScreenMargins? {
        val p = prefs(context)
        if (!p.contains(scopedKey(context, KEY_RECT_ML))) return null
        return ScreenMargins(
            p.getFloat(scopedKey(context, KEY_RECT_ML), 0f),
            p.getFloat(scopedKey(context, KEY_RECT_MT), 0f),
            p.getFloat(scopedKey(context, KEY_RECT_MR), 0f),
            p.getFloat(scopedKey(context, KEY_RECT_MB), 0f)
        )
    }

    fun loadTableMargins(context: Context): ScreenMargins? = loadLegacyTableMargins(context)

    fun saveTableMargins(context: Context, margins: ScreenMargins, locked: Boolean, sync: Boolean = false) {
        val editor = prefs(context).edit()
            .putFloat(scopedKey(context, KEY_RECT_ML), margins.left)
            .putFloat(scopedKey(context, KEY_RECT_MT), margins.top)
            .putFloat(scopedKey(context, KEY_RECT_MR), margins.right)
            .putFloat(scopedKey(context, KEY_RECT_MB), margins.bottom)
            .putBoolean(scopedKey(context, KEY_RECT_LOCKED), locked)
        if (sync) editor.commit() else editor.apply()
    }

    fun isLayoutLocked(context: Context): Boolean {
        return getBoolean(context, KEY_RECT_LOCKED, false)
    }

    fun loadAimMargins(context: Context): Pair<ScreenMargins, ScreenMargins>? {
        val p = prefs(context)
        if (!p.contains(scopedKey(context, KEY_AIM1_ML))) return null
        return ScreenMargins(
            p.getFloat(scopedKey(context, KEY_AIM1_ML), 0f),
            p.getFloat(scopedKey(context, KEY_AIM1_MT), 0f),
            0f,
            0f
        ) to ScreenMargins(
            p.getFloat(scopedKey(context, KEY_AIM2_ML), 0f),
            p.getFloat(scopedKey(context, KEY_AIM2_MT), 0f),
            0f,
            0f
        )
    }

    fun saveAimMargins(
        context: Context,
        point1: ScreenMargins,
        point2: ScreenMargins,
        sync: Boolean = false
    ) {
        val editor = prefs(context).edit()
            .putFloat(scopedKey(context, KEY_AIM1_ML), point1.left)
            .putFloat(scopedKey(context, KEY_AIM1_MT), point1.top)
            .putFloat(scopedKey(context, KEY_AIM2_ML), point2.left)
            .putFloat(scopedKey(context, KEY_AIM2_MT), point2.top)
        if (sync) editor.commit() else editor.apply()
    }

    fun defaultOverlayBounds(context: Context): OverlayPanelBounds {
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()
        val landscape = dm.widthPixels > dm.heightPixels ||
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (landscape) {
            val marginPercent = landscapeSideMarginPercent(context) / 100f
            val sideMargin = screenW * marginPercent
            OverlayPanelBounds(
                x = sideMargin,
                y = 0f,
                width = screenW - 2f * sideMargin,
                height = screenH
            )
        } else {
            OverlayPanelBounds(0f, 0f, screenW, screenH)
        }
    }

    fun defaultTogglePosition(context: Context): Pair<Float, Float> {
        val margin = 12f * context.resources.displayMetrics.density
        return margin to margin
    }

    private fun profileKeyPrefix(profileId: String) = "profile_${profileId}_"

    private fun scopedKey(context: Context, baseKey: String): String {
        val profile = activeProfileId(context)
        return if (profile == DEFAULT_PROFILE_ID) baseKey else profileKeyPrefix(profile) + baseKey
    }

    private fun getInt(context: Context, baseKey: String, default: Int): Int {
        return prefs(context).getInt(scopedKey(context, baseKey), default)
    }

    private fun putInt(context: Context, baseKey: String, value: Int) {
        prefs(context).edit().putInt(scopedKey(context, baseKey), value).apply()
    }

    private fun getBoolean(context: Context, baseKey: String, default: Boolean): Boolean {
        return prefs(context).getBoolean(scopedKey(context, baseKey), default)
    }

    private fun putBoolean(context: Context, baseKey: String, value: Boolean) {
        prefs(context).edit().putBoolean(scopedKey(context, baseKey), value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
