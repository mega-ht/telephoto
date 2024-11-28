package me.saket.telephoto.sample

import android.os.Bundle
import android.os.StrictMode
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.delay
import me.saket.telephoto.sample.gallery.MediaAlbum
import me.saket.telephoto.sample.gallery.MediaItem
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.util.concurrent.Executor

class SampleActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    if (BuildConfig.DEBUG) {
      // enableStrictMode()
    }
    enableEdgeToEdge()
    setupImmersiveMode()
    super.onCreate(savedInstanceState)

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
        Box {
          Images(
            onImageTap = { }
          )

          Icon(
            Icons.Default.PushPin,
            contentDescription = "Middle",
            modifier = Modifier.align(Alignment.Center)
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
          "file:///android_asset/thumbnail.jpeg",
          "file:///android_asset/thumbnail.jpeg",
        )
      )
    }

    LaunchedEffect(Unit) {
      delay(2000)
      imagePaths = Pair(
        "file:///android_asset/smallSize.jpeg",
        "file:///android_asset/thumbnail.jpeg"
      )

      delay(2000)
      imagePaths = Pair(
        "file:///android_asset/fullSize.jpeg",
        "file:///android_asset/smallSize.jpeg"
      )
    }

    ImageViewer(imagePaths.first, imagePaths.second, onImageTap)
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

@Composable
private fun ImageViewer(
  imagePath: String,
  previousImagePath: String?,
  onImageTap: () -> Unit
) {
  val zoomableState = rememberZoomableState(
    autoApplyTransformations = false
  )
  val zoomableImageState = rememberZoomableImageState(zoomableState)
  val offsetCache = remember {
    mutableStateMapOf<String, Offset>()
  }

  // Use derivedStateOf to compute values when the dependencies change
  val contentTransformationState = remember {
    derivedStateOf {
      zoomableImageState.zoomableState.contentTransformation.offset
    }
  }

  LaunchedEffect(contentTransformationState.value) {
    offsetCache[imagePath] = contentTransformationState.value
  }

  LaunchedEffect(imagePath, zoomableImageState.isImageDisplayedInFullQuality) {
    if (imagePath.contains("full") && zoomableImageState.isImageDisplayedInFullQuality) {
      val currentOffSet = zoomableState.contentTransformation.offset
      val previousImageOffset = offsetCache[previousImagePath] ?: Offset.Zero
      zoomableState.panBy(offset = previousImageOffset - currentOffSet, snap(0))
    }
  }

  val imageRequest = ImageRequest.Builder(LocalContext.current)
    .data(imagePath)
    .memoryCacheKey(imagePath)
    .placeholderMemoryCacheKey(previousImagePath)
    .crossfade(false)
    .build()

  ZoomableAsyncImage(
    model = imageRequest,
    contentDescription = "Image Preview",
    state = zoomableImageState,
    modifier = Modifier
      .fillMaxSize(),
    onClick = {
      onImageTap()
    }
  )
}
