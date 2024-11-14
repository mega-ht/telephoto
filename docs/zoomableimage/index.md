# Zoomable Image

![type:video](../assets/demo_small.mp4)

A _drop-in_ replacement for async `Image()` composables featuring support for pan & zoom gestures and automatic sub-sampling of large images. This ensures that images maintain their intricate details even when fully zoomed in, without causing any `OutOfMemory` exceptions. 

**Features**

- Automatic [sub-sampling](sub-sampling.md) of bitmaps
- Gestures:
  - Pinch-to-zoom and flings
  - Double click to zoom
  - Single finger zoom (double-tap and hold)
- Haptic feedback when reaching zoom limits
- Compatibility with nested scrolling
- Click listeners
- [Keyboard and mouse shortcuts](#keyboard-shortcuts)
- State preservation across config changes (including screen rotations)

### Installation

=== "Coil"
    ```groovy
    // For Coil 2.x
    implementation("me.saket.telephoto:zoomable-image-coil:{{ versions.telephoto }}")

    // For Coil 3.x
    implementation("me.saket.telephoto:zoomable-image-coil3:{{ versions.telephoto }}")
    ```
=== "Glide"
    ```groovy
    implementation("me.saket.telephoto:zoomable-image-glide:{{ versions.telephoto }}")
    ```
<!-- Invisible separator for tabbed code blocks -->
=== "Coil"
    ```diff
    - AsyncImage(
    + ZoomableAsyncImage(
        model = "https://example.com/image.jpg",
        contentDescription = …
      )
    ```
=== "Glide"
    ```diff
    - GlideImage(
    + ZoomableGlideImage(
        model = "https://example.com/image.jpg",
        contentDescription = …
      )
    ```

### Image requests

For complex scenarios, `ZoomableImage` can also take full image requests: 

=== "Coil"
    ```kotlin
    ZoomableAsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .listener(
          onSuccess = { … },
          onError = { … },
        )
        .crossfade(1_000)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build(),
      imageLoader = LocalContext.current.imageLoader, // Optional.
      contentDescription = …
    )
    ```

=== "Glide"
    ```kotlin
    ZoomableGlideImage(
      model = "https://example.com/image.jpg",
      contentDescription = …
    ) {
      it.addListener(object : RequestListener<Drawable> {
          override fun onResourceReady(…): Boolean = TODO()
          override fun onLoadFailed(…): Boolean = TODO()
        })
        .transition(withCrossFade(1_000))
        .skipMemoryCache(true)
        .disallowHardwareConfig()
        .timeout(30_000),
    }
    ```

### Placeholders

![type:video](../assets/placeholders_small.mp4)

If your images are available in multiple resolutions, `telephoto` highly recommends using their lower resolutions as placeholders while their full quality equivalents are loaded in the background.

When combined with a cross-fade transition, `ZoomableImage` will smoothly swap out placeholders when their full quality versions are ready to be displayed.

=== "Coil"
    ```kotlin hl_lines="5-6"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = ImageRequest.Builder(LocalContext.current)
        .data("https://example.com/image.jpg")
        .placeholderMemoryCacheKey(…)
        .crossfade(1_000)
        .build(),
      contentDescription = …
    )
    ```
    More details about `placeholderMemoryCacheKey()` can be found on [Coil's website](https://coil-kt.github.io/coil/recipes/#using-a-memory-cache-key-as-a-placeholder).

=== "Glide"
    ```kotlin hl_lines="6-7"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      contentDescription = …
    ) {
      it.thumbnail(…)   // or placeholder()
        .transition(withCrossFade(1_000)),
    }
    ```
    More details about `thumbnail()` can be found on [Glide's website](https://bumptech.github.io/glide/doc/options.html#thumbnail-requests).

!!! Warning
    Placeholders are visually incompatible with `Modifier.wrapContentSize()`.

### Content alignment

| ![type:video](../assets/alignment_top_small.mp4) | ![type:video](../assets/alignment_bottom_small.mp4) |
|:------------------------------------------------:|:---------------------------------------------------:|
|              `Alignment.TopCenter`               |              `Alignment.BottomCenter`               | 

When images are zoomed, they're scaled with respect to their `alignment` until they're large enough to fill all available space. After that, they're scaled uniformly. The default `alignment` is `Alignment.Center`.

=== "Coil"
    ```kotlin hl_lines="4"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      alignment = Alignment.TopCenter
    )
    ```
=== "Glide"
    ```kotlin hl_lines="4"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      alignment = Alignment.TopCenter
    )
    ```

### Content scale

| ![type:video](../assets/scale_inside_small.mp4) | ![type:video](../assets/scale_crop_small.mp4) |
|:-----------------------------------------------:|:---------------------------------------------:|
|              `ContentScale.Inside`              |              `ContentScale.Crop`              |

Images are scaled using `ContentScale.Fit` by default, but can be customized. A visual guide of all possible values can be found [here](https://developer.android.com/jetpack/compose/graphics/images/customize#content-scale).

Unlike `Image()`, `ZoomableImage` can pan images even when they're cropped. This can be useful for applications like wallpaper apps that may want to use `ContentScale.Crop` to ensure that images always fill the screen.

=== "Coil"
    ```kotlin hl_lines="4"
    ZoomableAsyncImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      contentScale = ContentScale.Crop
    )
    ```
=== "Glide"
    ```kotlin hl_lines="4"
    ZoomableGlideImage(
      modifier = Modifier.fillMaxSize(),
      model = "https://example.com/image.jpg",
      contentScale = ContentScale.Crop
    )
    ```

!!! Warning
    Placeholders are visually incompatible with `ContentScale.Inside`.

### Click listeners
For detecting double clicks, `ZoomableImage` consumes all tap gestures making it incompatible with `Modifier.clickable()` and `Modifier.combinedClickable()`. As an alternative, its `onClick` and `onLongClick` parameters can be used.

=== "Coil"
    ```kotlin hl_lines="4-5"
    ZoomableAsyncImage(
      modifier = Modifier.clickable { error("This will not work") },
      model = "https://example.com/image.jpg",
      onClick = { … },
      onLongClick = { … },
    )
    ```
=== "Glide"
    ```kotlin hl_lines="4-5"
    ZoomableGlideImage(
      modifier = Modifier.clickable { error("This will not work") },
      model = "https://example.com/image.jpg",
      onClick = { … },
      onLongClick = { … },
    )
    ```

The default behavior of toggling between minimum and maximum zoom levels on double-clicks can be overridden by using the `onDoubleClick` parameter:

=== "Coil" hl_lines="3"
    ```kotlin
    ZoomableAsyncImage(
      model = "https://example.com/image.jpg",
      onDoubleClick = { state, centroid -> … },
    )
    ```
=== "Glide"
    ```kotlin hl_lines="3"
    ZoomableGlideImage(
      model = "https://example.com/image.jpg",
      onDoubleClick = { state, centroid -> … },
    )
    ```

### Keyboard shortcuts

`ZoomableImage()` can observe keyboard and mouse shortcuts for panning and zooming when it is focused, either by the user or using a `FocusRequester`:

```kotlin hl_lines="6"
val focusRequester = remember { FocusRequester() }
LaunchedEffect(Unit) {
  // Automatically request focus when the image is displayed. This assumes there 
  // is only one zoomable image present in the hierarchy. If you're displaying 
  // multiple images in a pager, apply this only for the active page.  
  focusRequester.requestFocus()
}
```

=== "Coil"
    ```kotlin hl_lines="2"
    ZoomableAsyncImage(
      modifier = Modifier.focusRequester(focusRequester),
      model = "https://example.com/image.jpg",
    )
    ```
=== "Glide"
    ```kotlin hl_lines="2"
    ZoomableGlideImage(
      modifier = Modifier.focusRequester(focusRequester),
      model = "https://example.com/image.jpg",
    )
    ```

By default, the following shortcuts are recognized. These can be customized (or disabled) by passing a custom `HardwareShortcutsSpec` to `rememberZoomableState()`.

|           | Android            |
|-----------|--------------------|
| Zoom in   | `Control` + `=`    |
| Zoom out  | `Control` + `-`    |
| Pan       | Arrow keys         |
| Extra pan | `Alt` + arrow keys |

### Sharing hoisted state

For handling zoom gestures, `Zoomablemage` uses [`Modifier.zoomable()`](../zoomable/index.md) underneath. If your app displays different kinds of media, it is recommended to hoist the `ZoomableState` outside so that it can be shared with all zoomable composables:

=== "Coil"
    ```kotlin
    val zoomableState = rememberZoomableState()

    when (media) {
     is Image -> {
        ZoomableAsyncImage(
         model = media.imageUrl,
         state = rememberZoomableImageState(zoomableState),
        )
      }
      is Video -> {
        ZoomableVideoPlayer(
          model = media.videoUrl,
          state = rememberZoomableExoState(zoomableState),
        )
      }
    }
    ```
=== "Glide"
    ```kotlin
    val zoomableState = rememberZoomableState()

    when (media) {
     is Image -> {
        ZoomableGlideImage(
         model = media.imageUrl,
         state = rememberZoomableImageState(zoomableState),
        )
      }
      is Video -> {
        ZoomableVideoPlayer(
          model = media.videoUrl,
          state = rememberZoomableExoState(zoomableState),
        )
      }
    }
    ```
