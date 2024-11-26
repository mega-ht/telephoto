package me.saket.telephoto.sample

import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.facebook.common.internal.Supplier
import com.facebook.common.internal.Suppliers
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.fresco.vito.core.FrescoVitoConfig
import com.facebook.fresco.vito.core.PrefetchConfig
import com.facebook.fresco.vito.core.PrefetchTarget
import com.facebook.fresco.vito.init.FrescoVito
import com.facebook.fresco.vito.source.ImageSourceProvider
import com.facebook.imagepipeline.common.Priority
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.github.panpf.sketch.SingletonSketch
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ComposableImageRequest
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.resize.AsyncImageSizeResolver
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.state.MemoryCacheStateImage
import com.github.panpf.sketch.state.ThumbnailMemoryCacheStateImage
import com.github.panpf.sketch.util.key
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.SketchZoomAsyncImage
import com.github.panpf.zoomimage.compose.zoom.Transform
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.compose.zoom.zoomable
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.rememberSketchZoomState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.sample.viewer.MyThumbnailMemoryCacheStateImage
import me.saket.telephoto.sample.viewer.VitoImage
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import java.time.temporal.TemporalQueries.precision
import java.util.concurrent.Executor

class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    if (BuildConfig.DEBUG) {
      // enableStrictMode()
    }
    enableEdgeToEdge()
    setupImmersiveMode()
    super.onCreate(savedInstanceState)

    Fresco.initialize(
      applicationContext,
      ImagePipelineConfig.newBuilder(applicationContext)
        .setDownsampleEnabled(true)
        .build()
    )

    FrescoVito.initialize(
      vitoConfig = object : FrescoVitoConfig {
        override val prefetchConfig: PrefetchConfig
          get() = object : PrefetchConfig {
            override fun prefetchInOnBoundsDefinedForDynamicSize() = true

            override fun prefetchInOnPrepare() = true

            override fun prefetchTargetOnBoundsDefined() = PrefetchTarget.MEMORY_ENCODED

            override fun prefetchTargetOnPrepare() = PrefetchTarget.MEMORY_ENCODED
          }

        override fun enableWindowWideColorGamut() = false

        override fun experimentalDynamicSizeOnPrepareMainThreadVito2() = true

        override fun experimentalDynamicSizeVito2() = true

        override fun experimentalDynamicSizeWithCacheFallbackVito2() = true

        override fun fallbackToDefaultImageOptions() = true

        override fun fastPathForEmptyRequests() = true

        override fun handleImageResultInBackground() = true

        override fun layoutPrefetchingEnabled(callerContext: Any?) = true

        override fun onlyStopAnimationWhenAutoPlayEnabled() = true

        override fun stopAnimationInOnRelease() = true

        override fun submitFetchOnBgThread() = true

        override fun useBindOnly() = true

        override fun useIntermediateImagesAsPlaceholder() = true

        override fun useNativeRounding(): Supplier<Boolean>? = Suppliers.of(true)

        override fun useNewReleaseCallback() = true

        override fun useSmartPropertyDiffing() = true

      },
      // debugOverlayEnabledSupplier = Suppliers.of(true),
    )

    Coil.setImageLoader(
      ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .build()
    )

    val album = MediaAlbum(
      items = listOf(
        // Photo by Anita Austvika on https://unsplash.com/photos/yFxAORZcJQk.
        MediaItem.Image(
          fullSizedUrl = "https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65",
          placeholderImageUrl = "https://images.unsplash.com/photo-1678465952838-c9d7f5daaa65?w=100",
          caption = "Breakfast",
        ),
        // Photos by Romain Guy on https://www.flickr.com/photos/romainguy/.
        MediaItem.Image(
          fullSizedUrl = "https://live.staticflickr.com/65535/46217553745_fa38e0e7f0_o_d.jpg",
          placeholderImageUrl = "https://live.staticflickr.com/65535/46217553745_e8d9242548_w_d.jpg",
          caption = "Follow the light",
        ),
        MediaItem.Image(
          fullSizedUrl = "https://live.staticflickr.com/2809/11679312514_3f759b77cd_o_d.jpg",
          placeholderImageUrl = "https://live.staticflickr.com/2809/11679312514_7592396e9f_w_d.jpg",
          caption = "Flamingo",
        ),
        MediaItem.Image(
          fullSizedUrl = "https://live.staticflickr.com/6024/5911366388_600e7e6734_o_d.jpg",
          placeholderImageUrl = "https://i.imgur.com/bQtqkj6.jpg",
          caption = "Sierra Sunset",
        ),
      )
    )
    setContent {
      val systemUiController = rememberSystemUiController()
      val useDarkIcons = !isSystemInDarkTheme()
      LaunchedEffect(systemUiController, useDarkIcons) {
        systemUiController.setSystemBarsColor(
          color = Color.Transparent,
          darkIcons = useDarkIcons
        )
      }

      TelephotoTheme {
//        Navigation(
//          initialScreenKey = GalleryScreenKey(album)
//        )

//
//        SketchZoomAsyncImage(
//          request = ComposableImageRequest("file:///android_asset/fullSize.jpeg") {
//            placeholder(ThumbnailMemoryCacheStateImage("file:///android_asset/smallSize.jpeg"))
//            crossfade(fadeStart = false)
//          },
//          contentDescription = "view image",
//          modifier = Modifier.fillMaxSize(),
//        )


        val showToolbar = rememberSaveable { mutableStateOf(false) }

        // Mock loading images
        Images(
          onImageTap = {
            showToolbar.value = !showToolbar.value
            println("hmtz image tapped: $showToolbar")
          }
        )

        println("hmtz indies value ${showToolbar.value}")
        AnimatedVisibility(visible = showToolbar.value) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp)
              .background(Color.Red),
          )
        }
      }
    }
  }

  @Composable fun Images(
    onImageTap: () -> Unit = {}
  ) {
    var imagePaths by rememberSaveable {
      mutableStateOf(
        // First = high-res, second = low-res
        Pair<String, String?>(
          "file:///android_asset/thumbnail2.jpeg",
          "file:///android_asset/thumbnail2.jpeg",
        )
      )
    }
    LaunchedEffect(Unit) {
      delay(2000)
      imagePaths = Pair(
        "file:///android_asset/smallSize2.jpeg",
        "file:///android_asset/thumbnail2.jpeg"
      )

      delay(2000)
      imagePaths = Pair(
        "file:///android_asset/fullSize2.jpeg",
        "file:///android_asset/smallSize2.jpeg"
      )
    }

//    ImageViewerSketch(
//      imagePath = imagePaths.first,
//      previousImagePath = imagePaths.second,
//      onImageTap = {
//        onImageTap()
//      }
//    )

    val imageRequest = ImageRequest.Builder(LocalContext.current)
      .data(imagePaths.first)
      .memoryCacheKey(imagePaths.first)
      .placeholderMemoryCacheKey(imagePaths.second)
      .crossfade(false)
      .build()

    ZoomableAsyncImage(
      model = imageRequest,
      contentDescription = "Image Preview",
      modifier = Modifier
        .fillMaxSize()
        .zoomable(
          state = rememberZoomableState()
        )
    )

    AsyncImage(
      model = imageRequest,
      contentDescription = "Image Preview",
      modifier = Modifier
        .fillMaxSize()
        .zoomable(
          state = rememberZoomableState()
        )
    )

//    ImageViewerTele(
//      imagePathz = imagePaths.first,
//      previousImagePath = imagePaths.second,
//    )
  }

  @Composable
  private fun ImageViewerSketch(
    imagePath: String,
    previousImagePath: String?,
    onImageTap: () -> Unit = {}
  ) {

    println("hmtz imagePath: $imagePath, previousImagePath: $previousImagePath")

//    val imageRequest = ImageRequest.Builder(LocalContext.current)
//      .data(imagePath)
//      .memoryCacheKey(imagePath)
//      .placeholderMemoryCacheKey(previousImagePath)
//      .crossfade(false)
//      .build()
//
//    CoilZoomAsyncImage(
//      model = imageRequest,
//      contentDescription = "Image Preview",
//      modifier = Modifier.fillMaxSize(),
//      onTap = {
//        onImageTap()
//      }
//    )


    // Sketch
    val context = LocalContext.current
    val sketch = SingletonSketch.get(context)
    val zoomState = rememberSketchZoomState()
    val imageState = rememberAsyncImageState(
      options = ImageOptions {
        placeholder(ThumbnailMemoryCacheStateImage(uri = previousImagePath))
      }
    )
    SketchZoomAsyncImage(
      request = ComposableImageRequest(imagePath) {
        placeholder(ThumbnailMemoryCacheStateImage(previousImagePath))
        crossfade(fadeStart = false)
      },
      zoomState = zoomState,
      contentDescription = "view image",
      modifier = Modifier.fillMaxSize(),
      onTap = {
        onImageTap()
      }
    )


  }


  data class ZoomableStateInfo2(
    val transform: Transform,
    val contentSize: IntSize,
  )

  @Composable
  private fun ImageViewerAnother(
    imagePath: String,
    previousImagePath: String?,
  ) {
    val coroutineScope = rememberCoroutineScope()
    val zoomableStateCache = remember { mutableStateMapOf<String, ZoomableStateInfo2>() }


    val imageRequest = ImageRequest.Builder(LocalContext.current)
      .data(imagePath)
      .memoryCacheKey(imagePath)
      .placeholderMemoryCacheKey(previousImagePath)
      .crossfade(false)
      .build()

    val zoomableState = rememberCoilZoomState()

    LaunchedEffect(zoomableState.zoomable.userTransform, zoomableState.zoomable.contentOriginSize) {
      if (imagePath.contains("full") && zoomableState.zoomable.contentOriginSize.width != 0) {
        zoomableStateCache[imagePath] = ZoomableStateInfo2(
          transform = zoomableState.zoomable.userTransform.copy(),
          contentSize = zoomableState.zoomable.contentOriginSize
        )
      }
      if (imagePath.contains("small")) {
        zoomableStateCache[imagePath] = ZoomableStateInfo2(
          transform = zoomableState.zoomable.userTransform.copy(),
          contentSize = zoomableState.zoomable.contentSize
        )
      }
    }

    LaunchedEffect(zoomableState.zoomable.contentOriginSize) {
//      println("hmtz origin changed ${zoomableState.zoomable.contentOriginSize}")
//
//      println("hmtz $zoomableStateCache")
//      val currentImageSize = zoomableStateCache[imagePath]?.contentSize
//      val previousImageSize = zoomableStateCache[previousImagePath]?.contentSize
//      val previousImageOffset = zoomableStateCache[previousImagePath]?.transform?.offset
//
//      println("hmtz currentImageSize: $currentImageSize, previousImageSize: $previousImageSize")
//
//      val previousScale = zoomableStateCache[previousImagePath]?.transform?.scale?.scaleX
//      if (previousScale != null && previousScale != 1f && !imagePath.contains("thumb") && currentImageSize?.width != 0) {
//        val scaleFactor = previousImageSize?.width?.div(currentImageSize?.width ?: 1) ?: 1
//
//        println("hmtz previous scale: $previousScale, factor $scaleFactor")
//
//        val offset = Offset(
//          x = (previousImageOffset?.x?.times(scaleFactor) ?: 0f),
//          y = (previousImageOffset?.y?.times(scaleFactor) ?: 0f)
//        )
//
//        coroutineScope.launch {
//          zoomableState.zoomable.scale(
//            targetScale = previousScale,
//            centroidContentPoint = previousImageOffset?.round() ?: IntOffset(0, 0),
//          )
//
//          zoomableState.zoomable.locate(
//            contentPoint = previousImageOffset?.round() ?: IntOffset(0, 0),
//            targetScale = previousScale,
//          )

//          zoomableState.zoomable.offset(
//            targetOffset = offset
//          )
      //   }
      // }
    }

    CoilZoomAsyncImage(
      model = imageRequest,
      contentDescription = "view image",
      zoomState = zoomableState,
      onSuccess = {
        val previousTransform = zoomableStateCache[previousImagePath]?.transform
      },
      modifier = Modifier.fillMaxSize(),
    )
  }

  data class ZoomableStateInfo(
    val zoomFraction: Float?,
    val contentSize: Size,
    val centroid: Offset?,
    val offset: Offset?
  )


  @Composable
  private fun ImageViewerTele(
    imagePathz: String,
    previousImagePath: String?,
  ) {

    val coroutineScope = rememberCoroutineScope()
    val zoomableState = rememberZoomableState()
    val zoomableImageState = rememberZoomableImageState(zoomableState)
    // zoomableState.autoApplyTransformations = true
    val imagePath by remember(imagePathz) { mutableStateOf(imagePathz) }

    var previousImageCentroid by remember { mutableStateOf<Offset?>(null) }
    //val previousImageSizes = remember { mutableStateMapOf<String, Size>() }
    val zoomableStateCache = remember { mutableStateMapOf<String, ZoomableStateInfo>() }

    LaunchedEffect(zoomableState.zoomFraction) {
      zoomableStateCache[imagePathz] = ZoomableStateInfo(
        zoomFraction = zoomableState.zoomFraction,
        contentSize = zoomableState.contentTransformation.contentSize,
        centroid = zoomableState.contentTransformation.centroid,
        offset = zoomableState.contentTransformation.offset
      )
    }

    LaunchedEffect(zoomableImageState.isImageDisplayed, zoomableImageState) {
      val previousZoomFraction = zoomableStateCache[imagePath]?.zoomFraction
      println(
        "hmtz ${
          zoomableStateCache.toMap().map {
            it.key to it.value.contentSize
          }
        }"
      )

      if (zoomableImageState.isImageDisplayed && !imagePath.contains("thumbnail") && previousZoomFraction != null) {
        val previousOffset = zoomableStateCache[previousImagePath]?.centroid
        val currentImageSize = zoomableStateCache[imagePath]?.contentSize
        val previousImageSize = zoomableStateCache[previousImagePath]?.contentSize

        println("hmtz currentImageSize: $currentImageSize, previousImageSize: $previousImageSize")

        val scaleFactor = previousImageSize?.width?.div(currentImageSize?.width ?: 1f) ?: 1f

        val zoomFractionFull = (previousZoomFraction / scaleFactor)

        println("hmtz previousZoomFraction: $previousZoomFraction , zoomFractionFull: $zoomFractionFull")

        if (zoomFractionFull != 0f && previousOffset != null) {
          val offsetFull = Offset(
            x = previousOffset.x * scaleFactor,
            y = previousOffset.y * scaleFactor
          )

//          zoomableState.setCentroid(
//            centroid = offsetFull
//          )

//          zoomableState.zoomBy(
//            zoomFactor = zoomFractionFull,
//            centroid = offsetFull,
//            animationSpec = snap(0)
//          )

//          zoomableState.panBy(
//            // zoomFactor = scaleFactor,
//            offsetFull,
//            animationSpec = snap(0)
//          )

          //zoomableState.setContentLocation(ZoomableContentLocation.unscaledAndTopLeftAligned(currentImageSize))
        }
      }
    }

//    LaunchedEffect(imagePathz) {
//      if (imagePathz.contains("full")) {
//        println("hmtz previous zoom: $previousImageZoom")
//        zoomableState.resetZoom()
////        zoomableState.zoomBy(
////          zoomFactor = 0.5f,
////         //centroid = previousImageCentroid ?: Offset(0f, 0f),
////        )
//      }
//    }

    var isFullSizeImageLoaded by remember { mutableStateOf(false) }

    val imageRequest = ImageRequest.Builder(LocalContext.current)
      .data(imagePath)
      .memoryCacheKey(imagePath)
      .placeholderMemoryCacheKey(previousImagePath)
      .crossfade(false)
      .build()

    Box {
      // Change ZoomableAsyncImage to AsyncImage to stop flickering

//      if (!isFullSizeImageLoaded)
//        AsyncImage(
//          model = imageRequest,
//          onSuccess = {
//            // println("hmtz size: ${it.result.memoryCacheKey?.key} ${it.painter.intrinsicSize}")
//
////            it.result.memoryCacheKey?.key?.let { imagePath ->
////              previousImageSizes[imagePath] = it.painter.intrinsicSize
////            }
//
////            zoomableState.setContentLocation(
////              ZoomableContentLocation.scaledToFitAndCenterAligned(it.painter.intrinsicSize)
////            )
//
//            if (it.result.memoryCacheKey?.key?.contains("full") == true) {
//              coroutineScope.launch {
//                delay(500)
//                isFullSizeImageLoaded = true
//              }
//            }
//          },
//          contentDescription = "Image View",
//          modifier = Modifier
//            .fillMaxSize()
//        )

//      if (imagePath.contains("full"))
      ZoomableAsyncImage(
        model = imageRequest,
        state = zoomableImageState,
        contentDescription = "Image View",
        modifier = Modifier
          .fillMaxSize()
      )

      val imageState = rememberSubSamplingImageState(
        zoomableState = zoomableState,
        imageSource = SubSamplingImageSource.contentUri(imagePath.toUri())
      )


//      if (!imagePath.contains("full") || (imagePath.contains("full") && !zoomableImageState.isImageDisplayed)) {
//        AsyncImage(
//          model = imageRequest,
//          onSuccess = {
//            previousImageZoom = zoomableState.zoomFraction
//            previousImageCentroid = zoomableState.contentTransformation.centroid
//          },
//          contentDescription = "Image View",
//          modifier = Modifier
//              .fillMaxSize()
//              .zoomable(
//                  state = zoomableState
//              )
//        )
//      }
    }
  }

  private fun enableStrictMode() {
    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectAll()
        .penaltyDeath()
        .build()
    )
    StrictMode.setVmPolicy(
      StrictMode.VmPolicy.Builder()
        .detectLeakedClosableObjects()
        .penaltyListener(Executor(Runnable::run)) {
          // https://github.com/aosp-mirror/platform_frameworks_base/commit/e7ae30f76788bcec4457c4e0b0c9cbff2cf892f3
          if (!it.stackTraceToString().contains("sun.nio.fs.UnixSecureDirectoryStream.finalize")) {
            throw it
          }
        }
        .build()
    )
  }

  private fun setupImmersiveMode() {
    // Draw behind display cutouts.
    window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

    // No scrim behind transparent navigation bar.
    window.setFlags(FLAG_LAYOUT_NO_LIMITS, FLAG_LAYOUT_NO_LIMITS)

    // System bars use fade by default to hide/show. Make them slide instead.
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }
}

@Composable
internal fun TelephotoTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
    content = content
  )
}
