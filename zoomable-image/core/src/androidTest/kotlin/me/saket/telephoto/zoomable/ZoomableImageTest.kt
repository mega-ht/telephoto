@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable

import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.animation.core.SnapSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.device.action.ScreenOrientation
import assertk.all
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.dropbox.dropshots.Dropshots
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import leakcanary.LeakAssertions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.util.CiScreenshotValidator
import me.saket.telephoto.util.ScreenshotTestActivity
import me.saket.telephoto.util.waitUntil
import me.saket.telephoto.zoomable.ZoomableImageSource.ResolveResult
import me.saket.telephoto.zoomable.ZoomableImageTest.ScrollDirection
import me.saket.telephoto.zoomable.ZoomableImageTest.ScrollDirection.LeftToRight
import me.saket.telephoto.zoomable.ZoomableImageTest.ScrollDirection.RightToLeft
import org.junit.After
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(TestParameterInjector::class)
class ZoomableImageTest {
  @get:Rule val rule = createAndroidComposeRule<ScreenshotTestActivity>()
  @get:Rule val timeout = Timeout.seconds(30)!!
  @get:Rule val testName = TestName()

  private val screenshotValidator = CiScreenshotValidator(
    context = { rule.activity },
    tolerancePercentOnLocal = 0f,
    tolerancePercentOnCi = 0.01f,
  )
  @get:Rule val dropshots = Dropshots(
    filenameFunc = { it },
    resultValidator = screenshotValidator,
  )

  @Before fun setup() {
    // tearDown() should take care of resetting the orientation,
    // but there is a small chance that the previous test timed out.
    rule.setScreenOrientation(ScreenOrientation.PORTRAIT)
  }

  @After fun tearDown() {
    LeakAssertions.assertNoLeaks()
    rule.setScreenOrientation(ScreenOrientation.PORTRAIT)
  }

  @Test fun canary() {
    rule.setContent {
      ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
      )
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun zoom_in() {
    var imageScale = ScaleFactor.Unspecified

    rule.setContent {
      val zoomableState = rememberZoomableState().also {
        imageScale = it.contentTransformation.scale
      }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(zoomableState),
        contentDescription = null,
      )
    }

    rule.runOnIdle {
      assertThat(imageScale.scaleX).isEqualTo(1f)
    }
    rule.onNodeWithTag("image").performTouchInput {
      pinchToZoomInBy(visibleSize.center / 2f)
    }
    rule.runOnIdle {
      assertThat(imageScale.scaleX).isEqualTo(2f)
      assertThat(imageScale.scaleY).isEqualTo(2f)
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun retain_transformations_across_state_restorations(
    @TestParameter placeholderParam: UsePlaceholderParam,
    @TestParameter subSamplingStatus: SubSamplingStatus,
    @TestParameter canZoom: CanZoomParam,
  ) {
    val stateRestorationTester = StateRestorationTester(rule)
    val isPlaceholderVisible = MutableStateFlow(false)
    lateinit var state: ZoomableImageState

    stateRestorationTester.setContent {
      val imageSource = ZoomableImageSource.asset("fox_1500.jpg", subSample = subSamplingStatus.enabled).let {
        if (placeholderParam.canBeUsed) {
          it.withPlaceholder(assetPainter("fox_250.jpg"), isPlaceholderVisible)
        } else {
          it
        }
      }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = imageSource,
        contentDescription = null,
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
        ).also {
          state = it
        },
      )
    }

    rule.waitUntil(5.seconds) { state.isImageDisplayed }
    if (canZoom.canZoom) {
      with(rule.onNodeWithTag("image")) {
        performTouchInput {
          doubleClick()
        }
        performTouchInput {
          swipeLeft(startX = center.x, endX = centerLeft.x)
        }
      }
    }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_state_restoration")
    }

    isPlaceholderVisible.value = placeholderParam.canBeUsed
    stateRestorationTester.emulateSavedInstanceStateRestore()

    if (placeholderParam.canBeUsed) {
      rule.waitUntil(5.seconds) { state.isPlaceholderDisplayed }
      rule.runOnIdle {
        dropshots.assertSnapshot(rule.activity, testName.methodName + "_placeholder_after_state_restoration")
      }
    }

    isPlaceholderVisible.value = false
    rule.waitUntil(5.seconds) { state.isImageDisplayed }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_full_image_after_state_restoration")
    }
  }

  @Test fun various_image_sizes_and_alignments(
    @TestParameter alignment: AlignmentParam,
    @TestParameter contentScale: ContentScaleParam,
    @TestParameter imageAsset: ImageAssetParam,
    @TestParameter layoutSize: LayoutSizeParam,
    @TestParameter subSamplingStatus: SubSamplingStatus,
  ) {
    lateinit var state: ZoomableImageState
    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .then(layoutSize.modifier)
          .testTag("image"),
        image = ZoomableImageSource.asset(imageAsset.assetName, subSample = subSamplingStatus.enabled),
        contentDescription = null,
        state = rememberZoomableImageState().also { state = it },
        contentScale = contentScale.value,
        alignment = alignment.value,
      )
    }

    rule.waitUntil(5.seconds) { state.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      val by = visibleSize.center / 2f
      pinch(
        start0 = centerLeft,
        start1 = centerLeft,
        end0 = centerLeft - by.toOffset(),
        end1 = centerLeft + by.toOffset(),
      )
    }
    rule.waitUntil(5.seconds) { state.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed")
    }

    with(rule.onNodeWithTag("image")) {
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    rule.waitUntil(5.seconds) { state.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun rtl_layout_direction() {
    lateinit var state: ZoomableImageState

    rule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ZoomableImage(
          modifier = Modifier
            .fillMaxSize()
            .testTag("image"),
          image = ZoomableImageSource.asset("fox_1500.jpg", subSample = true),
          contentDescription = null,
          state = rememberZoomableImageState().also { state = it },
        )
      }
    }

    rule.waitUntil(5.seconds) { state.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.waitUntil(5.seconds) { state.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed")
    }

    with(rule.onNodeWithTag("image")) {
      performTouchInput {
        swipeLeft(startX = center.x, endX = centerLeft.x)
      }
    }
    rule.waitUntil(5.seconds) { state.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_zoomed_panned")
    }
  }

  @Test fun updating_of_content_alignment() {
    var contentAlignment by mutableStateOf(Alignment.BottomCenter)

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .fillMaxSize(),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        alignment = contentAlignment,
        contentDescription = null,
      )
    }
    dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_updating_alignment")

    contentAlignment = Alignment.TopCenter
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_after_updating_alignment")
    }
  }

  @Test fun updating_of_content_scale() {
    var contentScale by mutableStateOf(ContentScale.Crop)

    rule.setContent {
      ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentScale = contentScale,
        contentDescription = null,
      )
    }
    dropshots.assertSnapshot(rule.activity, testName.methodName + "_before_updating_scale")

    contentScale = ContentScale.Inside
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_after_updating_scale")
    }
  }

  @Test fun pager_can_be_scrolled_when_content_is_fully_zoomed_out_and_cannot_pan(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "fox_1500.jpg",
      "cat_1920.jpg"
    )

    rule.setContent {
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }),
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(
            rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 1f))
          ),
          contentDescription = null,
        )
      }
    }

    rule.onNodeWithTag("pager").performTouchInput {
      swipeWithVelocity(scrollDirection)
    }
    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun pager_can_be_scrolled_when_the_placeholder_is_visible(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "fox_1500.jpg",
      "cat_1920.jpg"
    )

    lateinit var pagerState: PagerState

    rule.setContent {
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }).also { pagerState = it },
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.placeholderOnly(assetPainter(assetNames[pageNum])),
          state = rememberZoomableImageState(
            rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 1f))
          ),
          contentDescription = null,
        )
      }
    }

    rule.onNodeWithTag("pager").performTouchInput {
      swipeWithVelocity(scrollDirection)
    }
    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      val expectedPageNum = when (scrollDirection) {
        RightToLeft -> 2
        LeftToRight -> 0
      }
      assertThat(pagerState.settledPage).isEqualTo(expectedPageNum)
    }
  }

  @Test fun pager_should_not_scroll_when_content_is_zoomed_in_and_can_pan(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "cat_1920.jpg",
      "fox_1500.jpg"
    )

    rule.setContent {
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }),
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(rememberZoomableState()),
          contentDescription = null,
        )
      }
    }

    with(rule.onNodeWithTag("pager")) {
      performTouchInput {
        pinchToZoomInBy(visibleSize.center / 2f)
      }
      performTouchInput {
        swipeWithVelocity(scrollDirection)
      }
    }

    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun pager_should_scroll_when_content_is_zoomed_in_but_cannot_pan(
    @TestParameter scrollDirection: ScrollDirection
  ) {
    val assetNames = listOf(
      "forest_fox_1000.jpg",
      "cat_1920.jpg",
      "fox_1500.jpg"
    )

    rule.setContent {
      HorizontalPager(
        modifier = Modifier.testTag("pager"),
        state = rememberPagerState(initialPage = 1, pageCount = { assetNames.size }),
      ) { pageNum ->
        ZoomableImage(
          modifier = Modifier.fillMaxSize(),
          image = ZoomableImageSource.asset(assetNames[pageNum], subSample = false),
          state = rememberZoomableImageState(
            rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 1.5f))
          ),
          contentDescription = null,
        )
      }
    }

    with(rule.onNodeWithTag("pager")) {
      performTouchInput {
        pinchToZoomInBy(visibleSize.center / 2f)
      }
      // First swipe will fully pan the content to its edge.
      // Second swipe should scroll the pager.
      performTouchInput {
        swipe(scrollDirection)
      }
      performTouchInput {
        swipe(scrollDirection)
      }
    }

    rule.mainClock.advanceTimeByFrame()
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun do_not_auto_reset_transformations_when_content_is_changed() {
    val maxZoomFactor = 2f
    var imageScale = ScaleFactor.Unspecified
    var assetName by mutableStateOf("fox_1500.jpg")

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor)
      ).also {
        imageScale = it.contentTransformation.scale
      }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset(assetName, subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(zoomableState),
      )
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
    }

    // It sounds weird that changing the image does not auto-reset transformations,
    // but the idea is that in the future it should be possible to load a low-quality
    // preview as a placeholder before loading the full image.
    assetName = "cat_1920.jpg"

    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(2f, 2f))
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @Test fun reset_content_transformations() {
    val maxZoomFactor = 2f
    var imageScale = ScaleFactor.Unspecified
    val resetTriggers = Channel<Unit>()
    val transformations = mutableListOf<ZoomableContentTransformation>()

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor)
      ).also {
        imageScale = it.contentTransformation.scale
      }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        state = rememberZoomableImageState(zoomableState),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
      )
      SideEffect {
        transformations.add(zoomableState.contentTransformation)
      }
      LaunchedEffect(resetTriggers) {
        resetTriggers.receive()
        zoomableState.resetZoom(animationSpec = SnapSpec())
      }
    }

    rule.onNodeWithTag("image").performTouchInput {
      doubleClick()
    }
    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
    }

    transformations.clear()
    resetTriggers.trySend(Unit)

    rule.runOnIdle {
      assertThat(imageScale).isEqualTo(ScaleFactor(1f, 1f))

      assertThat(transformations.filter { !it.isSpecified }).all(
        "Resetting the zoom shouldn't cause the transformations to become unusable. This will result in UI flickers",
      ) {
        isEmpty()
      }
    }
  }

  @Test fun zoom_fraction_is_correctly_calculated(
    @TestParameter scale: ContentScaleParam
  ) {
    var state: ZoomableImageState? = null
    fun zoomFraction() = state!!.zoomableState.zoomFraction

    rule.setContent {
      val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 3f)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(zoomableState).also { state = it },
        contentScale = scale.value,
        contentDescription = null,
      )
    }

    rule.waitUntil(5.seconds) { state!!.isImageDisplayed }

    val isImageStretchedToFill = when (scale) {
      ContentScaleParam.Crop -> true
      ContentScaleParam.Fit -> false
      ContentScaleParam.Inside -> false
      ContentScaleParam.Fill -> true
    }

    if (isImageStretchedToFill) {
      rule.runOnIdle {
        assertThat(zoomFraction()).isEqualTo(1f)
      }
    } else {
      rule.runOnIdle {
        assertThat(zoomFraction()).isEqualTo(0f)
      }

      rule.onNodeWithTag("image").performTouchInput {
        pinchToZoomInBy(IntOffset(0, 5))
      }
      rule.runOnIdle {
        assertThat(zoomFraction()!!).isEqualTo(1.0f)
      }

      rule.onNodeWithTag("image").performTouchInput {
        doubleClick()
      }
      rule.runOnIdle {
        assertThat(zoomFraction()).isEqualTo(0f)
      }
    }
  }

  @Test fun click_listeners_work_on_a_fully_loaded_image() {
    var clickCount = 0
    var longClickCount = 0
    val composableTag = "zoomable_image"

    rule.setContent {
      val state = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 1f)
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag(composableTag),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        contentScale = ContentScale.Inside,
        onClick = { clickCount++ },
        onLongClick = { longClickCount++ }
      )
    }

    rule.onNodeWithTag(composableTag).performClick()
    rule.runOnIdle {
      // Clicks are delayed until they're confirmed to not be double clicks
      // so make sure that onClick does not get called prematurely.
      assertThat(clickCount).isEqualTo(0)
    }
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clickCount).isEqualTo(1)
      assertThat(longClickCount).isEqualTo(0)
    }

    rule.onNodeWithTag(composableTag).performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(clickCount).isEqualTo(1)
      assertThat(longClickCount).isEqualTo(1)
    }

    // Regression testing for https://github.com/saket/telephoto/issues/18.
    // Perform double click to zoom in and make sure click listeners still work
    rule.onNodeWithTag(composableTag).performTouchInput { doubleClick() }
    rule.waitForIdle()

    rule.onNodeWithTag(composableTag).performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clickCount).isEqualTo(2)
    }

    rule.onNodeWithTag(composableTag).performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(longClickCount).isEqualTo(2)
    }

    // Perform double click to zoom out and make sure click listeners still work
    rule.onNodeWithTag(composableTag).performTouchInput { doubleClick() }
    rule.waitForIdle()

    rule.onNodeWithTag(composableTag).performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clickCount).isEqualTo(3)
    }

    rule.onNodeWithTag(composableTag).performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(longClickCount).isEqualTo(3)
    }
  }

  @Test fun click_listeners_work_on_a_placeholder_image() {
    var clicksCount = 0
    var longClicksCount = 0
    var doubleClicksCount = 0
    lateinit var imageState: ZoomableImageState

    rule.setContent {
      val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.placeholderOnly(ColorPainter(Color.Yellow)),
        contentDescription = null,
        state = rememberZoomableImageState(zoomableState).also { imageState = it },
        onClick = { clicksCount++ },
        onLongClick = { longClicksCount++ },
        onDoubleClick = { _, _ -> doubleClicksCount++ }
      )
    }
    rule.waitUntil { imageState.isPlaceholderDisplayed }

    rule.onNodeWithTag("image").performClick()
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(1)
      assertThat(longClicksCount).isEqualTo(0)
      assertThat(doubleClicksCount).isEqualTo(0)
    }

    rule.onNodeWithTag("image").performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(1)
      assertThat(longClicksCount).isEqualTo(1)
      assertThat(doubleClicksCount).isEqualTo(0)
    }

    rule.onNodeWithTag("image").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(clicksCount).isEqualTo(1)
      assertThat(longClicksCount).isEqualTo(1)
      assertThat(doubleClicksCount).isEqualTo(1)
    }
  }

  @Test fun quick_zooming_works() {
    val maxZoomFactor = 2f
    var currentScale = ScaleFactor.Unspecified
    var currentZoomFraction = 0f
    lateinit var state: RealZoomableState

    rule.setContent {
      state = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor)
      ).real()
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("zoomable"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        onClick = { error("click listener should not get called") },
        onLongClick = { error("long click listener should not get called") },
      )

      currentScale = state.contentTransformation.scale
      currentZoomFraction = state.zoomFraction ?: 0f
    }

    val zoomableNode = rule.onNodeWithTag("zoomable")
    zoomableNode.performTouchInput {
      quickZoomIn(byDistance = height.toFloat())
    }
    rule.runOnIdle {
      // Zoom should never cross the max zoom even if the above gesture over-zooms.
      assertThat(currentScale).isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
      assertThat(currentZoomFraction).isEqualTo(1f)
    }

    // Regression test: the migration of Modifier.zoomable() to Modifier.Node caused
    // a bug that prevented second quick-zooms from working. It would accept the first
    // quick-zoom gesture, but treat subsequent quick-zoom gestures as double-taps.
    assertThat(state.canConsumePanChange(Offset(1f, 1f))).all(
      "This bug happens only when the image can still be panned"
    ) {
      isTrue()
    }
    zoomableNode.performTouchInput {
      quickZoomIn(byDistance = height / 2f)
    }
    rule.runOnIdle {
      assertThat(currentScale).all(
        "Quick-zooming in again should not do anything because the image was already zoomed in"
      ) {
        isEqualTo(ScaleFactor(maxZoomFactor, maxZoomFactor))
      }
      assertThat(currentZoomFraction).isEqualTo(1f)
    }

    zoomableNode.performTouchInput {
      quickZoomOut(byDistance = 100f)
    }
    rule.runOnIdle {
      assertThat(currentScale.scaleY).isCloseTo(1.45f, delta = 0.05f)
      assertThat(currentZoomFraction).isCloseTo(0.45f, delta = 0.01f)
    }
    zoomableNode.performTouchInput {
      quickZoomOut(byDistance = 100f)
    }
    rule.runOnIdle {
      assertThat(currentScale.scaleY).isCloseTo(1.06f, delta = 0.01f)
      assertThat(currentZoomFraction).isCloseTo(0.06f, delta = 0.001f)
    }
  }

  @Test fun double_click_should_toggle_zoom() {
    lateinit var state: ZoomableState
    lateinit var composeScope: CoroutineScope

    rule.setContent {
      composeScope = rememberCoroutineScope()
      state = rememberZoomableState(
        zoomSpec = ZoomSpec()
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("zoomable"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(state),
        onClick = { error("click listener should not get called") },
        onLongClick = { error("long click listener should not get called") },
      )
    }

    rule.onNodeWithTag("zoomable").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(state.zoomFraction).isEqualTo(1f)
    }

    rule.onNodeWithTag("zoomable").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(state.zoomFraction).isEqualTo(0f)
    }

    // When the image is partially zoomed out, double clicking on it should zoom-in again.
    // This matches the original behavior before DoubleClickToZoomListener was introduced.
    composeScope.launch {
      state.zoomTo(zoomFactor = 1.8f)
    }
    rule.runOnIdle {
      assertThat(state.zoomFraction!!).isCloseTo(0.8f, delta = 0.01f)
    }
    rule.onNodeWithTag("zoomable").performTouchInput { doubleClick() }
    rule.runOnIdle {
      assertThat(state.zoomFraction).isEqualTo(1f)
    }
  }

  @Test fun gestures_are_ignored_when_gestures_are_disabled() {
    var state: ZoomableImageState? = null
    fun zoomFraction() = state!!.zoomableState.zoomFraction

    var onClickCalled = false
    var onLongClickCalled = false

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("fox_1500.jpg", subSample = false),
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
        ).also { state = it },
        contentDescription = null,
        gesturesEnabled = false,
        onClick = { onClickCalled = true },
        onLongClick = { onLongClickCalled = true },
      )
    }

    rule.onNodeWithTag("image").run {
      performTouchInput {
        pinchToZoomInBy(visibleSize.center / 2f)
      }
      performTouchInput {
        doubleClick()
      }
      performTouchInput {
        quickZoomIn()
      }
      performTouchInput {
        swipe(LeftToRight)
      }
      performTouchInput {
        swipeWithVelocity(RightToLeft)
      }
    }

    rule.runOnIdle {
      assertThat(zoomFraction()).isEqualTo(0f)
    }

    rule.onNodeWithTag("image").performTouchInput { longClick() }
    rule.runOnIdle {
      assertThat(onLongClickCalled).isTrue()
    }

    rule.onNodeWithTag("image").performTouchInput { click() }
    rule.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong())
    rule.runOnIdle {
      assertThat(onClickCalled).isTrue()
    }
  }

  @Test fun zoom_gestures_are_swallowed_on_a_placeholder_image() {
    lateinit var imageState: ZoomableImageState

    val dragEvents = mutableListOf<String>()
    val draggableState = DraggableState { dy ->
      dragEvents.add("dragged by $dy")
    }

    rule.setContent {
      Box(
        Modifier
          .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStarted = { dragEvents.add("drag started") },
          )
          .testTag("image_parent")
      ) {
        ZoomableImage(
          modifier = Modifier
            .fillMaxSize()
            .testTag("image"),
          image = ZoomableImageSource.placeholderOnly(assetPainter("fox_250.jpg")),
          contentDescription = null,
          state = rememberZoomableImageState().also { imageState = it },
        )
      }
    }
    rule.waitUntil { imageState.isPlaceholderDisplayed }

    rule.onNodeWithTag("image_parent").run {
      performTouchInput {
        val touchSlop = viewConfiguration.touchSlop.toInt()
        pinchToZoomInBy(IntOffset(touchSlop + 2, touchSlop + 2))
      }
      performTouchInput {
        quickZoomIn()
      }
    }
    rule.runOnIdle {
      assertThat(dragEvents).all(
        "Quick-zoom gestures made before the image is fully loaded should not be ignored. This will cause " +
          "FlickToDismiss() to accidentally flick the image. Same for other similar gesture parents."
      ) {
        isEmpty()
      }
    }
  }

  // Regression test for https://github.com/saket/telephoto/issues/33
  @Test fun panning_in_reverse_works_after_image_is_panned_to_the_edge() {
    lateinit var state: ZoomableImageState

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
        ).also { state = it },
      )
    }

    rule.waitUntil(5.seconds) { state.isImageDisplayed }
    rule.onNodeWithTag("image").performTouchInput {
      doubleClick(position = Offset.Zero)
    }

    // Scenario: when the image has reached its edge where it can't be panned any further,
    // changing the direction of the swipe gesture should pan the image in the opposite direction.
    rule.onNodeWithTag("image").performTouchInput {
      val noUpScope = object : TouchInjectionScope by this {
        override fun up(pointerId: Int) = Unit
      }
      noUpScope.swipeDown(startY = top, endY = centerY)

      val noDownScope = object : TouchInjectionScope by this {
        override fun down(pointerId: Int, position: Offset) = Unit
      }
      noDownScope.swipeUp(startY = centerY, endY = top)
    }

    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test fun pan_and_zoom_using_hardware_shortcuts() {
    lateinit var state: ZoomableImageState
    val maxZoomFactor = 5f

    rule.setContent {
      val focusRequester = remember { FocusRequester() }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .focusRequester(focusRequester)
          .testTag("image"),
        image = ZoomableImageSource
          .asset("cat_1920.jpg", subSample = false)
          .withDelay(500.milliseconds), // Ensures that the focus is received before the content is ready.
        contentDescription = null,
        state = rememberZoomableImageState(
          rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = maxZoomFactor))
        ).also {
          state = it
        },
      )
      LaunchedEffect(Unit) {
        // If the focus was received before the image was ready,
        // it should retain focus after the image becomes visible.
        assertThat(state.zoomableState.real().isReadyToInteract).isFalse()
        focusRequester.requestFocus()
      }
    }

    rule.waitUntil(5.seconds) { state.isImageDisplayed }

    // Zoom in.
    repeat(8) {
      rule.onNodeWithTag("image").performKeyInput {
        withKeyDown(Key.CtrlLeft) {
          pressKey(Key.Equals)
        }
      }
    }
    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(4.3f, 4.3f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-1781.9f, -3958.5f).toString())
      }
    }
    // Zoom out.
    repeat(2) {
      rule.onNodeWithTag("image").performKeyInput {
        withKeyDown(Key.CtrlRight) {
          pressKey(Key.Minus)
        }
      }
    }
    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(2.99f, 2.99f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-1072.4f, -2382.3f).toString())
      }
    }

    // Pan towards up.
    repeat(2) {
      rule.onNodeWithTag("image").performKeyInput {
        pressKey(Key.DirectionUp)
      }
    }
    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(2.99f, 2.99f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-1072.4f, -2119.8f).toString())
      }
    }
    // Pan towards down.
    repeat(2) {
      rule.onNodeWithTag("image").performKeyInput {
        pressKey(Key.DirectionDown)
      }
    }
    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(2.99f, 2.99f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-1072.4f, -2382.3f).toString())
      }
    }

    // Pan towards right.
    repeat(2) {
      rule.onNodeWithTag("image").performKeyInput {
        pressKey(Key.DirectionRight)
      }
    }
    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(2.99f, 2.99f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-1334.9f, -2382.3f).toString())
      }
    }
    // Pan towards left.
    repeat(2) {
      rule.onNodeWithTag("image").performKeyInput {
        pressKey(Key.DirectionLeft)
      }
    }
    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(2.99f, 2.99f).toString())
        assertThat(offset.toString()).isEqualTo(Offset(-1072.4f, -2382.3f).toString())
      }
    }

    // Zoom in using mouse.
    repeat(10) {
      rule.onNodeWithTag("image").performMultiModalInput {
        key {
          withKeyDown(Key.AltLeft) {
            mouse { scroll(-1f) }
          }
        }
      }
    }
    rule.runOnIdle {
      // Should not over-zoom.
      assertThat(state.zoomableState.contentTransformation.scale.toString()).isEqualTo(
        ScaleFactor(maxZoomFactor, maxZoomFactor).toString()
      )
    }
    // Zoom out using mouse.
    rule.onNodeWithTag("image").performMultiModalInput {
      key {
        withKeyDown(Key.AltLeft) {
          mouse { scroll(3f) }
        }
      }
    }
    rule.runOnIdle {
      assertThat(state.zoomableState.contentTransformation.scale.toString()).isEqualTo(
        ScaleFactor(1.4f, 1.4f).toString()
      )
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test fun hardware_shortcuts_are_ignored_when_shortcuts_are_disabled() {
    lateinit var state: ZoomableImageState
    val focusRequester = FocusRequester()

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .focusRequester(focusRequester)
          .testTag("image"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = false),
        contentDescription = null,
        state = rememberZoomableImageState(
          rememberZoomableState(
            hardwareShortcutsSpec = HardwareShortcutsSpec.Disabled,
          )
        ).also {
          state = it
        },
      )
    }

    val node = rule.onNodeWithTag("image").also {
      rule.waitUntil { state.isImageDisplayed }
    }

    // The image should not be focusable if it can't respond to keyboard and mouse events.
    focusRequester.requestFocus()
    node.assert(isNotFocusable())

    node.performKeyInput {
      withKeyDown(Key.CtrlLeft) {
        pressKey(Key.Equals)
      }
    }
    node.performKeyInput {
      pressKey(Key.DirectionLeft)
    }
    node.performMultiModalInput {
      key {
        withKeyDown(Key.AltLeft) {
          mouse { scroll(3f) }
        }
      }
    }

    rule.runOnIdle {
      state.zoomableState.contentTransformation.run {
        assertThat(scale.toString()).isEqualTo(ScaleFactor(1f, 1f).toString())
        assertThat(offset.toString()).isEqualTo(Offset.Zero.toString())
      }
    }
  }

  @Test fun hardware_shortcuts_do_not_break_the_back_button() {
    rule.setContent {
      val focusRequester = remember { FocusRequester() }
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .focusRequester(focusRequester)
          .testTag("image"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = false),
        contentDescription = null,
      )
      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    }

    rule.waitForIdle()
    rule.runOnUiThread {
      rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
      rule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }

    rule.waitUntil { rule.activity.lifecycle.currentState < Lifecycle.State.RESUMED }
    rule.onRoot().assertDoesNotExist()
  }

  @Test fun calculate_content_bounds_for_full_quality_images(
    @TestParameter subSamplingStatus: SubSamplingStatus,
  ) {
    lateinit var imageState: ZoomableImageState

    rule.setContent {
      val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
      Box(
        Modifier
          .fillMaxSize()
          .padding(24.dp)
      ) {
        ZoomableImage(
          modifier = Modifier
            .fillMaxSize()
            .testTag("image"),
          image = ZoomableImageSource.asset("forest_fox_1000.jpg", subSample = subSamplingStatus.enabled),
          contentDescription = null,
          state = rememberZoomableImageState(zoomableState).also { imageState = it },
        )
        Canvas(
          Modifier
            .matchParentSize()
            .clipToBounds()
        ) {
          val bounds = zoomableState.transformedContentBounds
          drawRect(
            color = Color.Yellow,
            topLeft = bounds.topLeft,
            size = bounds.size,
            style = Stroke(width = 2.dp.toPx()),
          )
        }
      }
    }
    rule.waitUntil { imageState.isImageDisplayed }
    dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_zoomed_out")

    rule.onNodeWithTag("image").run {
      performTouchInput { doubleClick() }
      performTouchInput { swipeRight() }
    }
    rule.waitUntil { imageState.zoomableState.zoomFraction == 1f }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_zoomed_in")
    }
  }

  @Test fun calculate_content_bounds_for_placeholder_images(
    @TestParameter placeholderParam: PlaceholderImageParam
  ) {
    lateinit var imageState: ZoomableImageState

    rule.setContent {
      val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
      Box(
        Modifier
          .fillMaxSize()
          .padding(24.dp)
      ) {
        ZoomableImage(
          modifier = Modifier
            .fillMaxSize()
            .testTag("image"),
          image = ZoomableImageSource.placeholderOnly(placeholderParam.painter()),
          contentDescription = null,
          state = rememberZoomableImageState(zoomableState).also { imageState = it },
        )
        Canvas(Modifier.matchParentSize()) {
          val bounds = zoomableState.transformedContentBounds
          drawRect(
            color = Color.Yellow,
            topLeft = bounds.topLeft,
            size = bounds.size,
            style = Stroke(width = 2.dp.toPx()),
          )
        }
      }
    }
    rule.waitUntil { imageState.isPlaceholderDisplayed }
    dropshots.assertSnapshot(rule.activity)
  }

  @Test fun image_without_an_intrinsic_size() {
    lateinit var imageState: ZoomableImageState

    rule.setContent {
      val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 2f))
      Box(
        Modifier
          .fillMaxSize()
          .padding(24.dp),
        Alignment.Center,
      ) {
        ZoomableImage(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = 0.4f)
            .testTag("image"),
          image = ZoomableImageSource.painter(ColorPainter(Color.Yellow)),
          contentDescription = null,
          state = rememberZoomableImageState(zoomableState).also { imageState = it },
          clipToBounds = false,
        )
      }
    }
    rule.waitUntil { imageState.isImageDisplayed }
    dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_zoomed_out")

    rule.onNodeWithTag("image").run {
      performTouchInput { doubleClick() }
      performTouchInput { swipeRight() }
    }
    rule.waitUntil { imageState.zoomableState.zoomFraction == 1f }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = testName.methodName + "_zoomed_in")
    }
  }

  @Test fun uses_updated_async_placeholder_size_when_available() = runTest {
    lateinit var state: ZoomableImageState

    val asyncPlaceholderPainter = PainterStub(initialSize = Size.Unspecified)
    val imageSource = object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        return ResolveResult(
          delegate = null,
          placeholder = asyncPlaceholderPainter,
        )
      }
    }

    rule.setContent {
      state = rememberZoomableImageState()
      ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        image = imageSource,
        contentDescription = null,
        state = state,
      )
    }

    // Bug description: https://github.com/saket/telephoto/pull/84
    // When the placeholder's intrinsic size is updated, the preview wasn't using the updated size.
    // When using an AsyncImagePainter from Coil, this was causing the preview to permanently use
    // Size.Unspecified, causing the placeholder to fill the view.
    rule.waitForIdle()
    asyncPlaceholderPainter.loadImage {
      rule.activity.assets.open("fox_250.jpg")
    }

    rule.waitUntil(5.seconds) { asyncPlaceholderPainter.loaded }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, name = "${testName.methodName}_placeholder")
    }
  }

  @Test fun gestures_dont_work_if_nothing_is_displayed() {
    var doubleClicked = false
    lateinit var imageState: ZoomableImageState

    rule.setContent {
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        state = rememberZoomableImageState().also { imageState = it },
        image = remember {
          object : ZoomableImageSource {
            @Composable
            override fun resolve(canvasSize: Flow<Size>) = ResolveResult(delegate = null)
          }
        },
        contentDescription = null,
        onDoubleClick = { _, _ -> doubleClicked = true },
      )
    }

    rule.waitForIdle()
    rule.onNodeWithTag("image").run {
      performTouchInput { doubleClick() }
      performTouchInput { pinchToZoomInBy(visibleSize.center / 2f) }
    }

    rule.runOnIdle {
      assertThat(doubleClicked).isFalse()
      assertThat(imageState.zoomableState.zoomFraction ?: 0f).isEqualTo(0f)
    }
  }

  // todo: should probably move these tests to ZoomableTest.
  @Test fun non_empty_transformations_are_retained_across_orientation_change(
    @TestParameter contentScaleParam: ContentScaleParamWithDifferentProportions,
    @TestParameter imageOrientationParam: ImageOrientationParam,
  ) {
    if (
      imageOrientationParam == ImageOrientationParam.Landscape
      && contentScaleParam != ContentScaleParamWithDifferentProportions.Fit
    ) {
      throw AssumptionViolatedException("not needed")
    }

    lateinit var imageState: ZoomableImageState

    val recreationTester = ActivityRecreationTester(rule)
    recreationTester.setContent {
      imageState = rememberZoomableImageState(
        rememberZoomableState(ZoomSpec(maxZoomFactor = 3f))
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .border(1.dp, Color.Yellow)
          .testTag("image"),
        image = ZoomableImageSource.asset(imageOrientationParam.assetName, subSample = true),
        state = imageState,
        contentDescription = null,
        contentScale = contentScaleParam.value,
      )
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    (rule.onNodeWithTag("image")).run {
      performTouchInput { doubleClick(center - Offset(0f, 360f)) }
    }
    rule.waitForIdle()
    rule.waitUntil { imageState.isImageDisplayedInFullQuality }

    val zoomFractionBeforeRotation = imageState.zoomableState.zoomFraction
    rule.runOnIdle {
      assertThat(zoomFractionBeforeRotation).isEqualTo(1f)
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[before_rotation]")
    }

    recreationTester.recreateWith {
      rule.setScreenOrientation(ScreenOrientation.LANDSCAPE)
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[after_rotation]")
    }

    recreationTester.recreateWith {
      rule.setScreenOrientation(ScreenOrientation.PORTRAIT)
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      assertThat(imageState.zoomableState.zoomFraction).isEqualTo(zoomFractionBeforeRotation)
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[after_another_rotation]")
    }
  }

  @Test fun empty_transformations_are_retained_across_orientation_change(
    @TestParameter contentScaleParam: ContentScaleParamWithDifferentProportions,
  ) {
    lateinit var imageState: ZoomableImageState

    val recreationTester = ActivityRecreationTester(rule)
    recreationTester.setContent {
      imageState = rememberZoomableImageState(
        rememberZoomableState(ZoomSpec(maxZoomFactor = 3f))
      )
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .border(1.dp, Color.Yellow)
          .testTag("image"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = true),
        state = imageState,
        contentDescription = null,
        contentScale = contentScaleParam.value,
      )
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[before_rotation]")
    }

    recreationTester.recreateWith {
      rule.setScreenOrientation(ScreenOrientation.LANDSCAPE)
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      assertThat(imageState.zoomableState.zoomFraction).isEqualTo(0f)
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[after_rotation]")
    }
  }

  @Test fun layout_changes_are_rendered_immediately_on_the_next_frame() {
    var topPadding by mutableStateOf(0.dp)
    var numOfRecompositions = 0

    lateinit var imageState: ZoomableImageState
    rule.setContent {
      imageState = rememberZoomableImageState()
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .padding(top = topPadding)
          .testTag("image"),
        image = ZoomableImageSource.asset("cat_1920.jpg", subSample = true),
        state = imageState,
        contentDescription = null,
      )

      SideEffect { numOfRecompositions++ }
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      assertThat(imageState.zoomableState.transformedContentBounds.top).isEqualTo(390f)
    }

    val numOfRecompositionsBeforeUpdate = numOfRecompositions
    topPadding = 150.dp

    // waitUntil or runOnIdle aren't used here because they can advance the time by multiple frames.
    rule.mainClock.advanceTimeByFrame()

    assertThat(imageState.zoomableState.transformedContentBounds.top).isEqualTo(193f)
    assertThat(numOfRecompositions).isEqualTo(numOfRecompositionsBeforeUpdate + 1)
  }

  @Test fun content_description_is_correctly_set() {
    val imageSource = object : ZoomableImageSource {
      var resolveResult: ResolveResult by mutableStateOf(
        ResolveResult(
          delegate = null,
          placeholder = null,
        )
      )

      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        return resolveResult
      }
    }

    lateinit var imageState: ZoomableImageState
    rule.setContent {
      imageState = rememberZoomableImageState()
      ZoomableImage(
        modifier = Modifier
          .fillMaxSize()
          .testTag("image"),
        image = imageSource,
        state = imageState,
        contentDescription = "nicolas cage",
      )
    }

    // Test case: neither the placeholder nor the full image have been loaded yet.
    assertThat(imageState.isImageDisplayed).isFalse()
    rule.onNodeWithTag("image").assertContentDescriptionEquals("nicolas cage")

    // Test case: placeholder only.
    val placeholderPainter = rule.activity.assets.open("cat_1920.jpg").use { stream ->
      BitmapPainter(BitmapFactory.decodeStream(stream).asImageBitmap())
    }
    imageSource.resolveResult = imageSource.resolveResult.copy(
      placeholder = placeholderPainter,
    )
    rule.waitUntil { imageState.isPlaceholderDisplayed }
    rule.onNodeWithTag("image").assertContentDescriptionEquals("nicolas cage")

    // Test case: a non-sub-sampled image is present.
    imageSource.resolveResult = imageSource.resolveResult.copy(
      delegate = ZoomableImageSource.PainterDelegate(placeholderPainter),
    )
    rule.waitUntil { imageState.isImageDisplayed }
    rule.onNodeWithTag("image").assertContentDescriptionEquals("nicolas cage")

    // Test case: sub-sampled image is present.
    imageSource.resolveResult = imageSource.resolveResult.copy(
      delegate = ZoomableImageSource.SubSamplingDelegate(SubSamplingImageSource.asset("cat_1920.jpg")),
    )
    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.onNodeWithTag("image").assertContentDescriptionEquals("nicolas cage")

    // Verify that ZoomableImage() clears its content description after
    // loading the full image, rather than retaining its initial description.
    rule.onAllNodesWithContentDescription("nicolas cage").assertCountEquals(1)
  }

  // Regression test for https://github.com/saket/telephoto/issues/114.
  @Test fun render_image_changes_immediately() {
    var resolvedZoomableImage by mutableStateOf(
      ResolveResult(
        delegate = ZoomableImageSource.SubSamplingDelegate(
          SubSamplingImageSource.asset(name = "fox_250.jpg", preview = null)
        ),
        placeholder = null,
      )
    )

    lateinit var imageState: ZoomableImageState
    rule.setContent {
      ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        image = object : ZoomableImageSource {
          @Composable override fun resolve(canvasSize: Flow<Size>): ResolveResult = resolvedZoomableImage
        },
        state = rememberZoomableImageState().also { imageState = it },
        contentDescription = null,
      )
    }

    rule.waitUntil { imageState.isImageDisplayedInFullQuality }
    rule.runOnIdle {
      dropshots.assertSnapshot(rule.activity, testName.methodName + "_[before_image_change]")
    }

    // When a new image is applied, its preview should be displayed immediately on
    // the next frame instead of waiting for the full image to be decoded from the disk.
    val newAssetName = "fox_1500.jpg"
    resolvedZoomableImage = ResolveResult(
      delegate = ZoomableImageSource.SubSamplingDelegate(
        SubSamplingImageSource.asset(
          name = newAssetName,
          // It would have been nice to use AsyncZoomableImage() directly here, but this
          // mimics how ZoomableImageSource.coil() sends the loaded image as a preview.
          preview = rule.activity.assets.open(newAssetName)
            .use(BitmapFactory::decodeStream)
            .asImageBitmap(),
        )
      ),
    )

    rule.mainClock.advanceTimeByFrame()
    dropshots.assertSnapshot(rule.activity, testName.methodName + "_[after_image_change]")
  }

  private class PainterStub(private val initialSize: Size) : Painter() {
    private var delegatePainter: Painter? by mutableStateOf(null)
    private var loaded = false

    override val intrinsicSize: Size
      get() = delegatePainter?.intrinsicSize ?: initialSize

    override fun DrawScope.onDraw() {
      delegatePainter?.run {
        draw(size)
      }
    }

    fun loadImage(imageStream: () -> InputStream) {
      delegatePainter = imageStream().use { stream ->
        BitmapPainter(BitmapFactory.decodeStream(stream).asImageBitmap())
      }
      loaded = true
    }
  }

  @Suppress("unused")
  enum class LayoutSizeParam(val modifier: Modifier) {
    FillMaxSize(Modifier.fillMaxSize()),
    WrapContent(Modifier.wrapContentSize()),
  }

  @Suppress("unused")
  enum class AlignmentParam(val value: Alignment) {
    TopCenter(Alignment.TopCenter),
    Center(Alignment.Center),
    BottomCenter(Alignment.BottomCenter),
  }

  @Suppress("unused")
  enum class ContentScaleParam(val value: ContentScale) {
    Crop(ContentScale.Crop),
    Fit(ContentScale.Fit),
    Inside(ContentScale.Inside),
    Fill(ContentScale.FillBounds),
  }

  @Suppress("unused")
  enum class ContentScaleParamWithDifferentProportions(val value: ContentScale) {
    Fit(ContentScale.Fit),          // Scaling is proportionate.
    Fill(ContentScale.FillBounds),  // Scaling is disproportionate
  }

  @Suppress("unused")
  enum class ImageOrientationParam(val assetName: String) {
    Portrait("cat_1920.jpg"),
    Landscape("fox_1500.jpg")
  }

  @Suppress("unused")
  enum class ImageAssetParam(val assetName: String) {
    SmallerThanLayoutSize("fox_250.jpg"),
    LargerThanLayoutSize("cat_1920.jpg")
  }

  @Suppress("unused")
  enum class SubSamplingStatus(val enabled: Boolean) {
    SubSamplingEnabled(enabled = true),
    SubSamplingDisabled(enabled = false),
  }

  @Suppress("unused")
  enum class UsePlaceholderParam(val canBeUsed: Boolean) {
    PlaceholderEnabled(canBeUsed = true),
    PlaceholderDisabled(canBeUsed = false),
  }

  @Suppress("unused")
  enum class CanZoomParam(val canZoom: Boolean) {
    CanZoom(canZoom = true),
    CanNotZoom(canZoom = false),
  }

  enum class ScrollDirection {
    RightToLeft,
    LeftToRight
  }

  @Suppress("unused")
  enum class PlaceholderImageParam(val painter: @Composable () -> Painter) {
    WithIntrinsicSize(painter = { assetPainter("fox_250.jpg") }),
    WithoutIntrinsicSize(painter = { ColorPainter(Color.Gray) }),
  }
}

private fun TouchInjectionScope.swipeWithVelocity(
  direction: ScrollDirection,
  velocity: Float = 5_000f,
) {
  when (direction) {
    RightToLeft -> swipeWithVelocity(
      start = centerRight,
      end = center,
      endVelocity = velocity,
    )

    LeftToRight -> swipeWithVelocity(
      start = centerLeft,
      end = center,
      endVelocity = velocity,
    )
  }
}

private fun TouchInjectionScope.swipe(
  direction: ScrollDirection
) {
  when (direction) {
    RightToLeft -> swipeLeft(
      startX = centerRight.x,
      endX = centerLeft.x,
    )

    LeftToRight -> swipeRight(
      startX = centerLeft.x,
      endX = centerRight.x,
    )
  }
}

internal fun TouchInjectionScope.pinchToZoomInBy(by: IntOffset) {
  pinch(
    start0 = center,
    start1 = center,
    end0 = center - by.toOffset(),
    end1 = center + by.toOffset(),
  )
}

private fun TouchInjectionScope.quickZoomIn(byDistance: Float = height / 2f) {
  val doubleTapMinTimeMillis = 40L // From LocalViewConfiguration.current.doubleTapMinTimeMillis.

  val start = center
  val endY = start.y + byDistance

  click(start)
  advanceEventTime(eventPeriodMillis + doubleTapMinTimeMillis)
  swipeDown(startY = start.y, endY = endY, durationMillis = 1_000)
}

private fun TouchInjectionScope.quickZoomOut(byDistance: Float = height / 2f) {
  val doubleTapMinTimeMillis = 40L // From LocalViewConfiguration.current.doubleTapMinTimeMillis.

  val start = bottomCenter
  val endY = start.y - byDistance

  click(start)
  advanceEventTime(doubleTapMinTimeMillis + 2)
  swipeUp(startY = start.y, endY = endY, durationMillis = 1_000)
}

@Composable
private fun assetPainter(assetName: String): Painter {
  val context = LocalContext.current
  return remember(assetName) {
    context.assets.open(assetName).use { stream ->
      BitmapPainter(BitmapFactory.decodeStream(stream).asImageBitmap())
    }
  }
}

@Composable
internal fun ZoomableImageSource.Companion.asset(assetName: String, subSample: Boolean): ZoomableImageSource {
  return remember(assetName) {
    object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        return ResolveResult(
          delegate = if (subSample) {
            ZoomableImageSource.SubSamplingDelegate(SubSamplingImageSource.asset(assetName))
          } else {
            ZoomableImageSource.PainterDelegate(assetPainter(assetName))
          }
        )
      }
    }
  }
}

@Composable
internal fun ZoomableImageSource.withDelay(duration: Duration): ZoomableImageSource {
  val delegate = this
  return remember(duration) {
    object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        val resolved = delegate.resolve(canvasSize)
        return produceState(initialValue = ResolveResult(delegate = null)) {
          delay(duration)
          this.value = resolved
        }.value
      }
    }
  }
}

@Composable
private fun ZoomableImageSource.withPlaceholder(
  placeholder: Painter,
  isPlaceholderVisible: StateFlow<Boolean>
): ZoomableImageSource {
  val delegate = this
  return remember(delegate, placeholder, isPlaceholderVisible) {
    object : ZoomableImageSource {
      @Composable
      override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        val showPlaceholder by isPlaceholderVisible.collectAsState()
        return when {
          showPlaceholder -> ResolveResult(delegate = null, placeholder = placeholder)
          else -> delegate.resolve(canvasSize).copy(placeholder = placeholder)
        }
      }
    }
  }
}

@Composable
private fun ZoomableImageSource.Companion.placeholderOnly(
  placeholder: Painter
): ZoomableImageSource {
  return remember(placeholder) {
    object : ZoomableImageSource {
      @Composable override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        return ResolveResult(
          delegate = null,
          placeholder = placeholder,
        )
      }
    }
  }
}

@Composable
private fun ZoomableImageSource.Companion.painter(
  painter: Painter
): ZoomableImageSource {
  return remember(painter) {
    object : ZoomableImageSource {
      @Composable override fun resolve(canvasSize: Flow<Size>): ResolveResult {
        return ResolveResult(
          delegate = ZoomableImageSource.PainterDelegate(painter),
          placeholder = null,
        )
      }
    }
  }
}
