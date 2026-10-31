package org.fossify.home.liquidglass

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposedModifierTag
import androidx.compose.ui.EditCommand
import androidx.compose.ui.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.kyant0.backdrop.Backdrop
import io.github.kyant0.backdrop.LayerBackdrop
import io.github.kyant0.backdrop.drawBackdrop
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.fossify.home.R

/**
 * Compose-based glass components for the app drawer.
 * These replace the View-based glass Drawables with proper Compose liquid glass.
 */

// ========================================================================
// Spring Physics Constants (from SKILL.md section 9)
// ========================================================================

object LiquidGlassSpringConstants {
    // Touch lift springs
    const val LIFT_STIFFNESS = 250f
    const val LIFT_DAMPING_RATIO = 0.6f

    // Travel spring (drawer open/close, search bar glide)
    const val TRAVEL_STIFFNESS = 280f
    const val TRAVEL_DAMPING = 31f

    // Glass spring (fast settle)
    const val GLASS_STIFFNESS = 1000f
    const val GLASS_DAMPING = 63.3f

    // Jelly stretch
    const val JELLY_VELOCITY_WINDOW_MS = 300f // 0.3 second window
    const val JELLY_MAX_STRETCH = 0.12f // 12% max stretch

    // Touch lift scale
    const val LIFT_SCALE = 1.06f // 6% lift (6-8% range)

    // Reduce animation check
    fun isReduceAnimationEnabled(context: Context): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1
        ) == 0
    }
}

// ========================================================================
// Glass Search Bar (Tier 1 - Full Glass) with Spring Physics
// ========================================================================

/**
 * Floating search pill with full liquid glass effect and spring animations.
 * Hosted inside a ComposeView in the AllAppsFragment.
 */
@Composable
fun LiquidGlassSearchBar(
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Boolean,
    hint: String = "Search apps...",
    imeAction: Int = EditorInfo.IME_ACTION_SEARCH,
    enabled: Boolean = true,
    isDrawerOpen: Boolean = true,
) {
    val shape = rememberRoundedShape() // Capsule shape
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val contentColor = glassContentColor()

    // Spring animation for drawer open/close
    val glideProgress by animateFloatAsState(
        targetValue = if (isDrawerOpen) 1f else 0f,
        animationSpec = spring(
            stiffness = LiquidGlassSpringConstants.TRAVEL_STIFFNESS,
            dampingRatio = LiquidGlassSpringConstants.TRAVEL_DAMPING / (2 * kotlin.math.sqrt(LiquidGlassSpringConstants.TRAVEL_STIFFNESS))
        ),
        label = "searchBarGlide"
    )

    // Touch lift animation
    var isPressed by remember { mutableStateOf(false) }
    val liftScale by animateFloatAsState(
        targetValue = if (isPressed) LiquidGlassSpringConstants.LIFT_SCALE else 1f,
        animationSpec = spring(
            stiffness = LiquidGlassSpringConstants.LIFT_STIFFNESS,
            dampingRatio = LiquidGlassSpringConstants.LIFT_DAMPING_RATIO
        ),
        label = "searchBarLift"
    )

    // Jelly stretch based on scroll velocity
    var lastScrollY by remember { mutableStateOf(0f) }
    var lastScrollTime by remember { mutableStateOf(0L) }
    val jellyStretch by remember { mutableStateOf(1f) }

    // Capsule shape (height/2 radius)
    val capsuleModifier = modifier
        .height(56.dp)
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = glideProgress * liftScale
            scaleY = glideProgress * liftScale
            alpha = glideProgress
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { press ->
                    press.tryAwaitRelease()
                    isPressed = false
                },
                onTap = { }
            )
        }

    Box(
        modifier = capsuleModifier
            .liquidGlassWithBitmap(
                backdropBitmap = getBackdropBitmap(context),
                shape = shape,
                lensHeightDp = 18f,
                lensAmountDp = 26f
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search icon
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )

            // Search text field
            androidx.compose.material3.TextField(
                value = TextFieldValue(
                    text = text,
                    selection = androidx.compose.ui.text.TextRange(text.length)
                ),
                onValueChange = { newValue ->
                    text = newValue.text
                    onQueryChange(newValue.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                            onSearchSubmit()
                            true
                        } else {
                            false
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { press ->
                                isPressed = true
                                press.tryAwaitRelease()
                                isPressed = false
                            }
                        )
                    },
                textStyle = androidx.compose.material3.Typography.bodyLarge.copy(
                    color = contentColor
                ),
                placeholder = {
                    Text(
                        text = hint,
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                },
                colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    textColor = contentColor,
                    placeholderTextColor = contentColor.copy(alpha = 0.5f),
                    cursorColor = contentColor,
                    trailingIconColor = contentColor.copy(alpha = 0.6f),
                ),
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                singleLine = true,
                keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                    onSearch = { onSearchSubmit() }
                ),
                visualTransformation = VisualTransformation.None
            )
        }
    }
}

// Helper to get backdrop bitmap
@Composable
private fun getBackdropBitmap(context: Context): android.graphics.Bitmap? {
    val activity = context as? androidx.appcompat.app.AppCompatActivity
    val screenWidth = activity?.resources?.displayMetrics?.widthPixels ?: 0
    val screenHeight = activity?.resources?.displayMetrics?.heightPixels ?: 0
    return LiquidGlassBackdrop.getBackdropBitmap(context, screenWidth, screenHeight)
}

// ========================================================================
// Glass Drag Handle / Top Grabber (Tier 1 - Full Glass) with Spring Physics
// ========================================================================

/**
 * Drag handle at the top of the drawer.
 * Small pill with full liquid glass and spring animations.
 */
@Composable
fun LiquidGlassDragHandle(
    modifier: Modifier = Modifier,
    onDrag: ((Float) -> Unit)? = null,
    isDrawerOpen: Boolean = true,
) {
    val shape = rememberRoundedShape(12f) // Small rounded pill
    val contentColor = glassContentColor()

    val glideProgress by animateFloatAsState(
        targetValue = if (isDrawerOpen) 1f else 0f,
        animationSpec = spring(
            stiffness = LiquidGlassSpringConstants.TRAVEL_STIFFNESS,
            dampingRatio = LiquidGlassSpringConstants.TRAVEL_DAMPING / (2 * kotlin.math.sqrt(LiquidGlassSpringConstants.TRAVEL_STIFFNESS))
        ),
        label = "dragHandleGlide"
    )

    Box(
        modifier = modifier
            .liquidGlassWithBitmap(
                backdropBitmap = getBackdropBitmap(LocalContext.current),
                shape = shape,
                lensHeightDp = LiquidGlassConstants.LENS_HEIGHT_SMALL_DP,
                lensAmountDp = LiquidGlassConstants.LENS_AMOUNT_SMALL_DP
            )
            .size(width = 48.dp, height = 4.dp)
            .padding(vertical = 8.dp)
            .graphicsLayer {
                alpha = glideProgress
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
    ) {
        // Handle content is just the glass itself
    }
}

// ========================================================================
// Glass Alphabet Fast Scroller (Tier 1 - Full Glass)
// ========================================================================

/**
 * Alphabet fast scroller on the right edge.
 * Vertical pill with full liquid glass.
 */
@Composable
fun LiquidGlassAlphabetScroller(
    modifier: Modifier = Modifier,
    currentLetter: String = "",
    onLetterClick: (String) -> Unit,
    letters: List<String> = ('A'..'Z').map { it.toString() } + "#"
) {
    val shape = rememberRoundedShape(16f)
    val contentColor = glassContentColor()

    Column(
        modifier = modifier
            .liquidGlassWithBitmap(
                backdropBitmap = getBackdropBitmap(LocalContext.current),
                shape = shape,
                lensHeightDp = LiquidGlassConstants.LENS_HEIGHT_SMALL_DP,
                lensAmountDp = LiquidGlassConstants.LENS_AMOUNT_SMALL_DP
            )
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .width(48.dp)
    ) {
        // Scroller content would go here - alphabet letters
        // For now, just the glass background
    }
}

// ========================================================================
// Glass Category Chips (Tier 2 - Lightweight Glass)
// ========================================================================

/**
 * Category filter chips using lightweight glass.
 * No blur/lens, just tint + rim + hairline.
 */
@Composable
fun LiquidGlassCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = rememberRoundedShape(20f)
    val contentColor = glassContentColor()
    val indicatorColor = glassIndicatorColor()

    val chipColor = if (selected) {
        indicatorColor.copy(alpha = 0.15f) // Subtle selected background
    } else {
        Color.Transparent
    }

    androidx.compose.material3.Text(
        text = label,
        color = if (selected) indicatorColor else contentColor,
        fontSize = 14.sp,
        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
        modifier = modifier
            .lightweightLiquidGlass(shape = shape, fallbackColor = chipColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .combine(
                androidx.compose.foundation.gestures.tapGestureDetector {
                    onClick()
                }
            )
    )
}

// ========================================================================
// Glass App Icon Press Highlight (Tier 2 - Lightweight Glass) with Spring Physics
// ========================================================================

/**
 * Pressed state highlight for app icons with jelly stretch physics.
 * Lightweight glass overlay on icon press.
 */
@Composable
fun LiquidGlassIconPressHighlight(
    modifier: Modifier = Modifier,
    pressed: Boolean,
    dragVelocity: Float = 0f
) {
    val shape = rememberRoundedShape(16f) // Icon corner radius

    // Jelly stretch based on velocity
    val stretch by animateFloatAsState(
        targetValue = if (pressed) {
            val stretchAmount = (abs(dragVelocity) * LiquidGlassSpringConstants.JELLY_MAX_STRETCH).coerceIn(-LiquidGlassSpringConstants.JELLY_MAX_STRETCH, LiquidGlassSpringConstants.JELLY_MAX_STRETCH)
            1f + stretchAmount
        } else 1f,
        animationSpec = spring(
            stiffness = LiquidGlassSpringConstants.GLASS_STIFFNESS,
            dampingRatio = LiquidGlassSpringConstants.GLASS_DAMPING / (2 * kotlin.math.sqrt(LiquidGlassSpringConstants.GLASS_STIFFNESS))
        ),
        label = "iconJellyStretch"
    )

    if (pressed) {
        Box(
            modifier = modifier
                .lightweightLiquidGlass(
                    shape = shape,
                    fallbackColor = Color.White.copy(alpha = 0.1f)
                )
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = stretch
                    scaleY = 2f - stretch // Squash in perpendicular direction
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
        )
    }
}

// ========================================================================
// Glass Long-Press Menu / Folder Popup (Tier 1 - Full Glass as in-screen overlay)
// ========================================================================

/**
 * In-screen overlay for long-press menu or folder popup.
 * Uses full glass since it's a floating overlay.
 * Must be drawn inside the same root as the backdrop layer (not a separate window).
 */
@Composable
fun LiquidGlassOverlay(
    modifier: Modifier = Modifier,
    shape: Shape = rememberRoundedShape(16f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .liquidGlassWithBitmap(
                backdropBitmap = getBackdropBitmap(LocalContext.current),
                shape = shape
            )
            .padding(16.dp)
    ) {
        content()
    }
}

// ========================================================================
// ComposeView Wrappers for View-based integration
// ========================================================================

/**
 * ComposeView that hosts the LiquidGlassSearchBar.
 * Can be placed in the XML layout where the old MySearchMenu was.
 */
class LiquidGlassSearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ComposeView(context, attrs, defStyleAttr) {

    var onQueryChange: (String) -> Unit = {}
    var onSearchSubmit: () -> Boolean = { false }
    var hint: String = context.getString(R.string.search_apps_hint)
    var isDrawerOpen: Boolean = true

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setContent {
            MaterialTheme {
                LiquidGlassSettingsProvider {
                    LiquidGlassSearchBar(
                        onQueryChange = onQueryChange,
                        onSearchSubmit = onSearchSubmit,
                        hint = hint,
                        isDrawerOpen = isDrawerOpen
                    )
                }
            }
        }
    }
}

/**
 * ComposeView for the drag handle.
 */
class LiquidGlassDragHandleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ComposeView(context, attrs, defStyleAttr) {

    var isDrawerOpen: Boolean = true

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setContent {
            MaterialTheme {
                LiquidGlassSettingsProvider {
                    LiquidGlassDragHandle(
                        isDrawerOpen = isDrawerOpen
                    )
                }
            }
        }
    }
}