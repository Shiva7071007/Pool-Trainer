package com.example.shichak

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var exitButton: Button
    private lateinit var createProfileButton: Button
    private lateinit var deleteProfileButton: Button
    private lateinit var profileSpinner: Spinner
    private lateinit var redDragLabel: TextView
    private lateinit var greenDragLabel: TextView
    private lateinit var redDragSeek: SeekBar
    private lateinit var greenDragSeek: SeekBar
    private lateinit var landscapeMarginLabel: TextView
    private lateinit var landscapeMarginSeek: SeekBar
    private lateinit var aimLineAlphaLabel: TextView
    private lateinit var reboundAlphaLabel: TextView
    private lateinit var reboundCountLabel: TextView
    private lateinit var lineThicknessLabel: TextView
    private lateinit var aimLineAlphaSeek: SeekBar
    private lateinit var reboundAlphaSeek: SeekBar
    private lateinit var reboundCountSeek: SeekBar
    private lateinit var lineThicknessSeek: SeekBar
    private lateinit var aimDragOffsetSwitch: SwitchCompat
    private lateinit var angleHudSwitch: SwitchCompat

    private var profileIds: List<String> = emptyList()
    private var suppressProfileSelection = false
    private lateinit var profileAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.btn_commence)
        exitButton = findViewById(R.id.btn_exit)
        createProfileButton = findViewById(R.id.btn_create_profile)
        deleteProfileButton = findViewById(R.id.btn_delete_profile)
        profileSpinner = findViewById(R.id.spinner_profile)
        redDragLabel = findViewById(R.id.label_red_drag)
        greenDragLabel = findViewById(R.id.label_green_drag)
        redDragSeek = findViewById(R.id.seek_red_drag)
        greenDragSeek = findViewById(R.id.seek_green_drag)
        landscapeMarginLabel = findViewById(R.id.label_landscape_margin)
        landscapeMarginSeek = findViewById(R.id.seek_landscape_margin)
        aimLineAlphaLabel = findViewById(R.id.label_aim_line_alpha)
        reboundAlphaLabel = findViewById(R.id.label_rebound_alpha)
        reboundCountLabel = findViewById(R.id.label_rebound_count)
        lineThicknessLabel = findViewById(R.id.label_line_thickness)
        aimLineAlphaSeek = findViewById(R.id.seek_aim_line_alpha)
        reboundAlphaSeek = findViewById(R.id.seek_rebound_alpha)
        reboundCountSeek = findViewById(R.id.seek_rebound_count)
        lineThicknessSeek = findViewById(R.id.seek_line_thickness)
        aimDragOffsetSwitch = findViewById(R.id.switch_aim_drag_offset)
        angleHudSwitch = findViewById(R.id.switch_angle_hud)

        profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        profileSpinner.adapter = profileAdapter

        setupProfileUi()
        setupDragConfig()
        setupLandscapeMarginConfig()
        setupLineConfig()
        setupToggleConfig()

        startButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                commenceShichak()
            } else {
                requestOverlayPermission()
            }
        }

        exitButton.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        requestNotificationPermissionIfNeeded()
        updateStartButtonState()
        refreshProfileSpinner()
        loadAllConfig()
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }
    }

    private fun setupProfileUi() {
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressProfileSelection) return
                val selectedId = profileIds.getOrNull(position) ?: return
                if (selectedId != ShichakPrefs.activeProfileId(this@MainActivity)) {
                    ShichakPrefs.switchProfile(this@MainActivity, selectedId)
                    loadAllConfig()
                    notifyProfileChanged()
                }
                updateDeleteProfileButton()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        createProfileButton.setOnClickListener { showCreateProfileDialog() }
        deleteProfileButton.setOnClickListener { showDeleteProfileDialog() }
    }

    private fun refreshProfileSpinner() {
        suppressProfileSelection = true
        profileIds = ShichakPrefs.allProfileIds(this)
        val labels = profileIds.map { id ->
            if (id == ShichakPrefs.DEFAULT_PROFILE_ID) {
                getString(R.string.profile_default_name)
            } else {
                id
            }
        }
        profileAdapter.clear()
        profileAdapter.addAll(labels)
        val activeIndex = profileIds.indexOf(ShichakPrefs.activeProfileId(this)).coerceAtLeast(0)
        profileSpinner.setSelection(activeIndex)
        suppressProfileSelection = false
        updateDeleteProfileButton()
    }

    private fun updateDeleteProfileButton() {
        deleteProfileButton.isEnabled =
            ShichakPrefs.activeProfileId(this) != ShichakPrefs.DEFAULT_PROFILE_ID
    }

    private fun showCreateProfileDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.profile_create_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_create_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> Toast.makeText(
                        this,
                        R.string.profile_name_invalid,
                        Toast.LENGTH_SHORT
                    ).show()
                    !ShichakPrefs.createProfile(this, name) -> Toast.makeText(
                        this,
                        R.string.profile_name_exists,
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> {
                        refreshProfileSpinner()
                        loadAllConfig()
                        notifyProfileChanged()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteProfileDialog() {
        val profileId = ShichakPrefs.activeProfileId(this)
        if (profileId == ShichakPrefs.DEFAULT_PROFILE_ID) return

        AlertDialog.Builder(this)
            .setTitle(R.string.profile_delete_title)
            .setMessage(getString(R.string.profile_delete_message, profileId))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ShichakPrefs.deleteProfile(this, profileId)
                refreshProfileSpinner()
                loadAllConfig()
                notifyProfileChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupDragConfig() {
        redDragSeek.setOnSeekBarChangeListener(dragListener { progress ->
            ShichakPrefs.saveRedDragPercent(this, progress)
            updateRedDragLabel(progress)
        })
        greenDragSeek.setOnSeekBarChangeListener(dragListener { progress ->
            ShichakPrefs.saveGreenDragPercent(this, progress)
            updateGreenDragLabel(progress)
        })
    }

    private fun setupLandscapeMarginConfig() {
        landscapeMarginSeek.setOnSeekBarChangeListener(dragListener { progress ->
            ShichakPrefs.saveLandscapeSideMarginPercent(this, progress)
            updateLandscapeMarginLabel(progress)
        })
    }

    private fun setupLineConfig() {
        val minAlpha = ShichakPrefs.MIN_LINE_ALPHA_PERCENT
        aimLineAlphaSeek.setOnSeekBarChangeListener(dragListener(minAlpha) { progress ->
            ShichakPrefs.saveAimLineAlphaPercent(this, progress)
            updateAimLineAlphaLabel(progress)
            notifyOverlayConfigChanged()
        })
        reboundAlphaSeek.setOnSeekBarChangeListener(dragListener(minAlpha) { progress ->
            ShichakPrefs.saveReboundAlphaPercent(this, progress)
            updateReboundAlphaLabel(progress)
            notifyOverlayConfigChanged()
        })
        reboundCountSeek.setOnSeekBarChangeListener(dragListener(0) { progress ->
            ShichakPrefs.saveReboundCount(this, progress)
            updateReboundCountLabel(progress)
            notifyOverlayConfigChanged()
        })
        lineThicknessSeek.setOnSeekBarChangeListener(dragListener(0) { progress ->
            val level = progress + ShichakPrefs.MIN_LINE_THICKNESS_LEVEL
            ShichakPrefs.saveLineThicknessLevel(this, level)
            updateLineThicknessLabel(level)
            notifyOverlayConfigChanged()
        })
    }

    private fun setupToggleConfig() {
        aimDragOffsetSwitch.setOnCheckedChangeListener { _, checked ->
            ShichakPrefs.saveAimDragOffsetEnabled(this, checked)
            notifyOverlayConfigChanged()
        }
        angleHudSwitch.setOnCheckedChangeListener { _, checked ->
            ShichakPrefs.saveAngleHudEnabled(this, checked)
            notifyOverlayConfigChanged()
        }
    }

    private fun loadAllConfig() {
        loadDragConfig()
        loadLandscapeMarginConfig()
        loadLineConfig()
        loadToggleConfig()
    }

    private fun loadDragConfig() {
        val red = ShichakPrefs.redDragPercent(this)
        val green = ShichakPrefs.greenDragPercent(this)
        redDragSeek.progress = red
        greenDragSeek.progress = green
        updateRedDragLabel(red)
        updateGreenDragLabel(green)
    }

    private fun loadLandscapeMarginConfig() {
        val margin = ShichakPrefs.landscapeSideMarginPercent(this)
        landscapeMarginSeek.progress = margin
        updateLandscapeMarginLabel(margin)
    }

    private fun loadLineConfig() {
        val aimAlpha = ShichakPrefs.aimLineAlphaPercent(this)
        val reboundAlpha = ShichakPrefs.reboundAlphaPercent(this)
        val reboundCount = ShichakPrefs.reboundCount(this)
        val thicknessLevel = ShichakPrefs.lineThicknessLevel(this)
        aimLineAlphaSeek.progress = aimAlpha
        reboundAlphaSeek.progress = reboundAlpha
        reboundCountSeek.progress = reboundCount
        lineThicknessSeek.progress = thicknessLevel - ShichakPrefs.MIN_LINE_THICKNESS_LEVEL
        updateAimLineAlphaLabel(aimAlpha)
        updateReboundAlphaLabel(reboundAlpha)
        updateReboundCountLabel(reboundCount)
        updateLineThicknessLabel(thicknessLevel)
    }

    private fun loadToggleConfig() {
        aimDragOffsetSwitch.isChecked = ShichakPrefs.isAimDragOffsetEnabled(this)
        angleHudSwitch.isChecked = ShichakPrefs.isAngleHudEnabled(this)
    }

    private fun updateLandscapeMarginLabel(percent: Int) {
        landscapeMarginLabel.text = getString(R.string.config_landscape_side_margin, percent)
    }

    private fun updateRedDragLabel(percent: Int) {
        redDragLabel.text = getString(R.string.config_red_drag, percent)
    }

    private fun updateGreenDragLabel(percent: Int) {
        greenDragLabel.text = getString(R.string.config_green_drag, percent)
    }

    private fun updateAimLineAlphaLabel(percent: Int) {
        aimLineAlphaLabel.text = getString(R.string.config_aim_line_alpha, percent)
    }

    private fun updateReboundAlphaLabel(percent: Int) {
        reboundAlphaLabel.text = getString(R.string.config_rebound_alpha, percent)
    }

    private fun updateReboundCountLabel(count: Int) {
        reboundCountLabel.text = getString(R.string.config_rebound_count, count)
    }

    private fun updateLineThicknessLabel(level: Int) {
        lineThicknessLabel.text = getString(R.string.config_line_thickness, level)
    }

    private fun dragListener(min: Int = 1, onChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                onChanged(progress.coerceAtLeast(min))
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATIONS
                )
            }
        }
    }

    private fun updateStartButtonState() {
        val granted = Settings.canDrawOverlays(this)
        startButton.isEnabled = granted
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun commenceShichak() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
        moveTaskToBack(true)
    }

    private fun notifyOverlayConfigChanged() {
        sendBroadcast(
            Intent(OverlayService.ACTION_CONFIG_CHANGED).setPackage(packageName)
        )
    }

    private fun notifyProfileChanged() {
        sendBroadcast(
            Intent(OverlayService.ACTION_PROFILE_CHANGED).setPackage(packageName)
        )
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}
