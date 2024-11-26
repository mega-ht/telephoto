package me.saket.telephoto.sample.viewer

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.facebook.fresco.vito.options.ImageOptions
import com.facebook.fresco.vito.source.ImageSource
import com.facebook.fresco.vito.view.VitoView

@Composable
fun VitoImage(
  source: ImageSource,
  callerContext: Any,
  modifier: Modifier = Modifier,
  options: ImageOptions = ImageOptions.defaults(),
) {
  AndroidView(
    modifier = modifier,
    factory = { ImageView(it) },
    update = {
      VitoView.show(
        imageSource = source,
        target = it,
        imageOptions = options,
        callerContext = callerContext,
      )
    },
    onRelease = { VitoView.release(it) },
  )
}
