package org.fossify.home.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import android.view.View
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.home.activities.MainActivity
import org.fossify.home.adapters.LaunchersAdapter
import org.fossify.home.databinding.AllAppsFragmentBinding
import android.graphics.Point
import org.fossify.commons.extensions.realScreenSize
import org.fossify.home.extensions.config
import org.fossify.home.extensions.launchApp
import org.fossify.home.helpers.ITEM_TYPE_ICON
import org.fossify.home.interfaces.AllAppsListener
import org.fossify.home.liquidglass.LiquidGlassBackdrop
import org.fossify.home.liquidglass.LiquidGlassComponents.LiquidGlassDragHandleView
import org.fossify.home.liquidglass.LiquidGlassComponents.LiquidGlassSearchBarView
import org.fossify.home.liquidglass.LiquidGlassComposeModifiers.liquidGlassWithBitmap
import org.fossify.home.liquidglass.LiquidGlassComposeModifiers.liquidGlassRim
import org.fossify.home.liquidglass.LiquidGlassHelpers.glassContentColor
import org.fossify.home.liquidglass.LiquidGlassHelpers.glassIndicatorColor
import org.fossify.home.liquidglass.LiquidGlassHelpers.isGlassSupported
import org.fossify.home.liquidglass.LiquidGlassHelpers.liquidGlassTintColor
import org.fossify.home.liquidglass.LiquidGlassHelpers.LocalLiquidGlassEnabled
import org.fossify.home.liquidglass.LiquidGlassHelpers.LocalReduceBlur
import org.fossify.home.liquidglass.LiquidGlassHelpers.rememberRoundedShape
import org.fossify.home.models.AppLauncher
import org.fossify.home.models.HomeScreenGridItem

class AllAppsFragment(
    context: Context,
    attributeSet: AttributeSet
) : MyFragment<AllAppsFragmentBinding>(context, attributeSet), AllAppsListener {

    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false

    private var launchers = emptyList<AppLauncher>()

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        this.binding = AllAppsFragmentBinding.bind(this)

        binding.allAppsGrid.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val screenSize = activity?.realScreenSize ?: Point(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        background = LiquidGlassDrawerDrawable(this, screenSize.x, screenSize.y)
    }


    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (binding.allAppsGrid.layoutManager == null || binding.allAppsGrid.adapter == null) {
            return
        }

        val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.config.drawerColumnCount) {
            onConfigurationChanged()
            // Force redraw due to changed item size
            (binding.allAppsGrid.adapter as LaunchersAdapter).notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        binding.allAppsGrid.scrollToPosition(0)
        binding.allAppsFastscroller.resetManualScrolling()
        setupViews()

        val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.config.drawerColumnCount
        setupAdapter(launchers)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

        var shouldIntercept = false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreTouches) {
                    // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
                    if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                        touchDownY = -1
                        return true
                    }
                }

                // pull the whole fragment down if it is scrolled way to the top and the user pulls it even further
                if (touchDownY != -1) {
                    val distance = event.y.toInt() - touchDownY
                    shouldIntercept =
                        distance > 0 && binding.allAppsGrid.computeVerticalScrollOffset() == 0
                    if (shouldIntercept) {
                        // Hiding is expensive, only do it if focused
                        if (binding.searchBar.hasFocus()) {
                            activity?.hideKeyboard()
                        }
                        activity?.startHandlingTouches(touchDownY)
                        touchDownY = -1
                    }
                }
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        return shouldIntercept
    }

    fun gotLaunchers(appLaunchers: List<AppLauncher>) {
        launchers = appLaunchers.sortedWith(
            compareBy(
                { it.title.normalizeString().lowercase() },
                { it.packageName }
            )
        )

        setupAdapter(launchers)
    }

    private fun getAdapter() = binding.allAppsGrid.adapter as? LaunchersAdapter

    private fun setupAdapter(launchers: List<AppLauncher>) {
        activity?.runOnUiThread {
            val layoutManager = binding.allAppsGrid.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.config.drawerColumnCount

            if (getAdapter() == null) {
                LaunchersAdapter(activity!!, this) {
                    activity?.launchApp((it as AppLauncher).packageName, it.activityName)
                    if (activity?.config?.closeAppDrawer == true) {
                        activity?.closeAppDrawer(delayed = true)
                    }
                    ignoreTouches = false
                    touchDownY = -1
                }.apply {
                    binding.allAppsGrid.itemAnimator = null
                    binding.allAppsGrid.adapter = this
                }
            }

            submitList(launchers.toMutableList())
        }
    }

    fun onIconHidden(item: HomeScreenGridItem) {
        val itemToRemove = launchers.firstOrNull {
            it.getLauncherIdentifier() == item.getItemIdentifier()
        }

        if (itemToRemove != null) {
            val position = launchers.indexOfFirst {
                it.getLauncherIdentifier() == item.getItemIdentifier()
            }

            launchers = launchers.toMutableList().apply {
                removeAt(position)
            }

            submitList(launchers.toMutableList())
        }
    }

    fun setupViews() {
        if (activity == null) {
            return
        }

        binding.allAppsFastscroller.updateColors(context.getProperPrimaryColor())
        binding.allAppsGrid.addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Hiding is expensive, only do it if focused
                if (binding.searchBar.hasFocus() && dy > 0 && binding.allAppsGrid.computeVerticalScrollOffset() > 0) {
                    activity?.hideKeyboard()
                }
            }
        })

        val screenSize = activity?.realScreenSize ?: Point(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

        // Set up the backdrop with current drawer progress
        LiquidGlassBackdrop.desktopView = activity?.binding?.homeScreenGrid?.root
        LiquidGlassBackdrop.drawerProgress = 1f - (y / screenSize.y).coerceIn(0f, 1f)

        // Clear backgrounds - glass will be drawn by ComposeViews
        background = null
        binding.allAppsGrid.background = null
        binding.allAppsFastscroller.background = null

        // Set up Compose-based glass search bar
        setupGlassSearchBar(screenSize.x, screenSize.y)

        // Set up drag handle at the top
        setupDragHandle(screenSize.x, screenSize.y)
    }

    private fun setupGlassSearchBar(screenWidth: Int, screenHeight: Int) {
        // Hide the old search bar
        binding.searchBar.visibility = View.GONE

        // Generate unique IDs for the ComposeViews
        val dragHandleId = View.generateViewId()
        val searchBarId = View.generateViewId()

        // Create or update the ComposeView for the drag handle first (since search bar is below it)
        val dragHandleComposeView = binding.root.findViewById<ComposeView>(dragHandleId) ?:
            ComposeView(context).apply {
                id = dragHandleId
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                    setMargins(
                        0,
                        resources.getDimensionPixelSize(R.dimen.medium_margin),
                        0,
                        0
                    )
                }
                binding.root.addView(this)
            }

        // Create or update the ComposeView for the search bar
        val searchBarComposeView = binding.root.findViewById<ComposeView>(searchBarId) ?:
            ComposeView(context).apply {
                id = searchBarId
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                layoutParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(android.widget.RelativeLayout.BELOW, dragHandleId)
                    setMargins(
                        resources.getDimensionPixelSize(R.dimen.activity_margin),
                        resources.getDimensionPixelSize(R.dimen.medium_margin),
                        resources.getDimensionPixelSize(R.dimen.activity_margin),
                        resources.getDimensionPixelSize(R.dimen.small_margin)
                    )
                }
                binding.root.addView(this)
            }

        // Get the backdrop bitmap
        val backdropBitmap = LiquidGlassBackdrop.getBackdropBitmap(context, screenWidth, screenHeight)

        dragHandleComposeView.setContent {
            MaterialTheme {
                LiquidGlassSettingsProvider(context) {
                    LiquidGlassComponents.LiquidGlassDragHandle(
                        modifier = Modifier
                            .liquidGlassWithBitmap(
                                backdropBitmap = backdropBitmap,
                                shape = rememberRoundedShape(12f),
                                lensHeightDp = 12f,
                                lensAmountDp = 14f
                            )
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(),
                        isDrawerOpen = true
                    )
                }
            }
        }

        searchBarComposeView.setContent {
            MaterialTheme {
                LiquidGlassSettingsProvider(context) {
                    LiquidGlassComponents.LiquidGlassSearchBar(
                        modifier = Modifier
                            .liquidGlassWithBitmap(
                                backdropBitmap = backdropBitmap,
                                shape = rememberRoundedShape(), // Capsule shape
                                lensHeightDp = 18f,
                                lensAmountDp = 26f
                            )
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onQueryChange = { query ->
                            submitList(launchers.filter {
                                it.title.normalizeString().contains(query.normalizeString(), ignoreCase = true)
                            })
                        },
                        onSearchSubmit = {
                            getAdapter()?.launchFirstApp() == true
                        },
                        hint = context.getString(R.string.search_apps_hint),
                        isDrawerOpen = true
                    )
                }
            }
        }
    }

    private fun setupDragHandle(screenWidth: Int, screenHeight: Int) {
        // This is now handled in setupGlassSearchBar to ensure proper ordering
    }

    private fun showNoResultsPlaceholderIfNeeded() {
        val itemCount = getAdapter()?.itemCount
        binding.noResultsPlaceholder.beVisibleIf(itemCount != null && itemCount == 0)
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher) {
        val gridItem = HomeScreenGridItem(
            id = null,
            left = -1,
            top = -1,
            right = -1,
            bottom = -1,
            page = 0,
            packageName = appLauncher.packageName,
            activityName = appLauncher.activityName,
            title = appLauncher.title,
            type = ITEM_TYPE_ICON,
            className = "",
            widgetId = -1,
            shortcutId = "",
            icon = null,
            docked = false,
            parentId = null,
            drawable = appLauncher.drawable
        )

        activity?.showHomeIconMenu(x, y, gridItem, true)
        ignoreTouches = true

        binding.searchBar.closeSearch()
    }

    fun onBackPressed(): Boolean {
        if (binding.searchBar.isSearchOpen) {
            binding.searchBar.closeSearch()
            return true
        }

        return false
    }

    private fun submitList(items: List<AppLauncher>) {
        val searchQuery = binding.searchBar.getCurrentQuery()
        val filtered = if (searchQuery.isNotEmpty()) {
            items.filter {
                it.title.normalizeString()
                    .contains(searchQuery.normalizeString(), ignoreCase = true)
            }
        } else {
            items
        }

        getAdapter()?.submitList(filtered) {
            showNoResultsPlaceholderIfNeeded()
        }
    }
}