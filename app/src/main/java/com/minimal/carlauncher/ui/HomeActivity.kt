package com.minimal.carlauncher.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.minimal.carlauncher.CarLauncherApp
import com.minimal.carlauncher.R
import com.minimal.carlauncher.core.Prefs
import com.minimal.carlauncher.databinding.ActivityHomeBinding
import com.minimal.carlauncher.core.RadioFrequency
import com.minimal.carlauncher.util.IntentUtil
import kotlinx.coroutines.launch

/** Offline HOME: no vehicle ViewModel, location, map, tuner session or updater. */
class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var drawer: DrawerController
    private lateinit var cards: QuickCardsController
    private val app: CarLauncherApp get() = application as CarLauncherApp
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        drawer = DrawerController(this, binding, app.appRepository, lifecycleScope)
        cards = QuickCardsController(this, binding, app.appRepository, lifecycleScope)
        drawer.bind()
        cards.bind()
        binding.btnAllApps.setOnClickListener { drawer.open() }
        binding.btnRadio.setOnClickListener { openRadio() }
        binding.btnRadio.setOnLongClickListener { pickRadio(); true }
        binding.btnSettings.setOnClickListener { openSettings() }
        binding.headerSettings.setOnClickListener { openSettings() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { drawer.close() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.appRepository.apps.collect {
                        drawer.submitApps(it)
                        cards.refreshLabels()
                    }
                }
                launch {
                    app.appRepository.packageRemoved.collect { pkg ->
                        cards.onPackageRemoved(pkg)
                        if (Prefs.radioPackage?.let { IntentUtil.packageOf(it) } == pkg) Prefs.radioPackage = null
                    }
                }
            }
        }
    }

    private fun openRadio() {
        val stored = Prefs.radioPackage ?: app.appRepository.apps.value.firstOrNull {
            RadioFrequency.looksLikeRadioPackage(it.packageName) ||
                it.sortKey == "radio" || it.sortKey == "rádio" ||
                it.sortKey == "fm" || it.sortKey.startsWith("fm ")
        }?.packageName
        if (stored != null && IntentUtil.launchStored(this, stored, binding.btnRadio)) return
        Prefs.radioPackage = null
        pickRadio()
    }

    private fun pickRadio() {
        AppPicker.show(this, app.appRepository, lifecycleScope, R.string.pick_radio_app) {
            Prefs.radioPackage = it.component.flattenToShortString()
        }
    }

    private fun openSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.l200_settings)
            .setItems(arrayOf(getString(R.string.pick_radio_app), getString(R.string.l200_home))) { _, index ->
                when (index) {
                    0 -> pickRadio()
                    1 -> IntentUtil.openHomeSettings(this)
                }
            }
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (leftAt > 0 && SystemClock.elapsedRealtime() - leftAt > 30_000) drawer.reset()
        cards.refreshLabels()
    }

    override fun onPause() {
        leftAt = SystemClock.elapsedRealtime()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) drawer.reset()
    }
}
