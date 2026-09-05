package com.pixelcraftin.pdfeditorplus

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.pixelcraftin.pdfeditorplus.data.prefs.AppPreferences
import com.pixelcraftin.pdfeditorplus.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefs: AppPreferences

    // Track current tab
    private var currentNavId = R.id.homeFragment

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            showPdfActionBottomSheet(selectedUri)
        }
    }

    private val pickMultipleImagesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            val bundle = com.pixelcraftin.pdfeditorplus.ui.documenteditor.DocumentEditorFragment.createBundle(uris)
            navController.navigate(R.id.documentCropFragment, bundle)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)

        // Apply saved theme before inflation
        lifecycleScope.launch {
            val theme = prefs.getTheme()
            applyTheme(theme)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupNavigation()
        setupBottomNav()
        setupFab()
        handleIncomingIntent(intent)
    }

    private fun setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { view, insets ->
            val statusBarInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBarInset.top, 0, 0)
            insets
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavContainer) { view, insets ->
            val navBarInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val params = view.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            if (params != null) {
                val density = resources.displayMetrics.density
                params.bottomMargin = (19 * density).toInt() + navBarInset.bottom
                view.layoutParams = params
            }
            insets
        }
    }

    private fun showPdfActionBottomSheet(uri: android.net.Uri) {
        val sheet = com.pixelcraftin.pdfeditorplus.ui.common.PdfActionBottomSheet.newInstance(uri)
        sheet.onChangeFileRequested = {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }
        sheet.show(supportFragmentManager, "PdfActionBottomSheet")
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentNavId = destination.id
            updateNavUI(destination.id)
            when (destination.id) {
                R.id.homeFragment, R.id.toolsFragment, R.id.historyFragment, R.id.settingsFragment -> showBottomNav()
                else -> hideBottomNav()
            }
        }
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener { navigateTo(R.id.homeFragment) }
        binding.navTools.setOnClickListener { navigateTo(R.id.toolsFragment) }
        binding.navHistory.setOnClickListener { navigateTo(R.id.historyFragment) }
        binding.navSettings.setOnClickListener { navigateTo(R.id.settingsFragment) }
    }

    private fun navigateTo(destinationId: Int) {
        if (destinationId == R.id.homeFragment) {
            // Always pop back to homeFragment safely from any screen or depth
            if (navController.currentDestination?.id != R.id.homeFragment) {
                val popped = navController.popBackStack(R.id.homeFragment, false)
                if (!popped) {
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, true)
                        .setLaunchSingleTop(true)
                        .setEnterAnim(android.R.anim.fade_in)
                        .setExitAnim(android.R.anim.fade_out)
                        .build()
                    navController.navigate(R.id.homeFragment, null, navOptions)
                }
            }
        } else {
            if (navController.currentDestination?.id != destinationId) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, false)
                    .setLaunchSingleTop(true)
                    .setEnterAnim(android.R.anim.fade_in)
                    .setExitAnim(android.R.anim.fade_out)
                    .setPopEnterAnim(android.R.anim.fade_in)
                    .setPopExitAnim(android.R.anim.fade_out)
                    .build()
                navController.navigate(destinationId, null, navOptions)
            }
        }
        currentNavId = destinationId
        updateNavUI(destinationId)
    }

    private fun updateNavUI(selectedId: Int) {
        val selectedColor = getColor(R.color.bottom_nav_selected)
        val unselectedColor = getColor(R.color.bottom_nav_unselected)

        fun setNav(navLayout: View, iconView: ImageView, labelView: TextView, isSelected: Boolean) {
            val color = if (isSelected) selectedColor else unselectedColor
            iconView.setColorFilter(color)
            labelView.setTextColor(color)
        }

        setNav(binding.navHome, binding.navHomeIcon, binding.navHomeLabel, selectedId == R.id.homeFragment)
        setNav(binding.navTools, binding.navToolsIcon, binding.navToolsLabel, selectedId == R.id.toolsFragment)
        setNav(binding.navHistory, binding.navHistoryIcon, binding.navHistoryLabel, selectedId == R.id.historyFragment)
        setNav(binding.navSettings, binding.navSettingsIcon, binding.navSettingsLabel, selectedId == R.id.settingsFragment)
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            // Trigger Android's native multi-image picker (supporting 200+ images without OOM)
            pickMultipleImagesLauncher.launch("image/*")
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        // Handle VIEW intents from other apps (e.g. open PDF)
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                // Navigate to Tools and pre-select file
                navigateTo(R.id.toolsFragment)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    fun hideBottomNav() {
        binding.bottomNavContainer.visibility = View.GONE
    }

    fun showBottomNav() {
        binding.bottomNavContainer.visibility = View.VISIBLE
    }

    fun applyTheme(theme: String) {
        when (theme) {
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
