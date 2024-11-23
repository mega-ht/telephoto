package me.saket.telephoto.zoomable.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import me.saket.telephoto.zoomable.HardwareShortcutDetector
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent.PanDirection
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent.ZoomDirection
import kotlin.math.absoluteValue

internal object DefaultHardwareShortcutDetector : HardwareShortcutDetector {

  override fun detectKey(event: KeyEvent): ShortcutEvent? {
    // Note for self: Some devices/peripherals have dedicated zoom buttons that map to Key.ZoomIn
    // and Key.ZoomOut. Examples include: Samsung Galaxy Camera, a motorcycle handlebar controller.
    if (event.key == Key.ZoomIn || event.isZoomInEvent()) {
      return ShortcutEvent.Zoom(ZoomDirection.In)
    } else if (event.key == Key.ZoomOut || (event.isZoomOutEvent())) {
      return ShortcutEvent.Zoom(ZoomDirection.Out)
    }

    val panDirection = when (event.key) {
      Key.DirectionUp -> PanDirection.Up
      Key.DirectionDown -> PanDirection.Down
      Key.DirectionLeft -> PanDirection.Left
      Key.DirectionRight -> PanDirection.Right
      else -> null
    }
    return when (panDirection) {
      null -> null
      else -> ShortcutEvent.Pan(
        direction = panDirection,
        panOffset = ShortcutEvent.DefaultPanOffset * if (event.isAltPressed) 10f else 1f,
      )
    }
  }

  private fun KeyEvent.isZoomInEvent(): Boolean {
    return this.key == Key.Equals && when (HostPlatform.current) {
      HostPlatform.Android, HostPlatform.iOS -> isCtrlPressed
      HostPlatform.Desktop -> isMetaPressed
    }
  }

  private fun KeyEvent.isZoomOutEvent(): Boolean {
    return key == Key.Minus && when (HostPlatform.current) {
      HostPlatform.Android, HostPlatform.iOS -> isCtrlPressed
      HostPlatform.Desktop -> isMetaPressed
    }
  }

  override fun detectScroll(event: PointerEvent): ShortcutEvent? {
    if (!event.keyboardModifiers.isAltPressed) {
      // Google Photos does not require any modifier key to be pressed for zooming into
      // images using mouse scroll. Telephoto does not follow the same pattern because
      // it might migrate to 2D scrolling in the future for panning content once Compose
      // UI supports it.
      return null
    }
    return when (val scrollY = event.calculateScroll().y) {
      0f -> null
      else -> ShortcutEvent.Zoom(
        direction = if (scrollY < 0f) ZoomDirection.In else ZoomDirection.Out,
        centroid = event.calculateScrollCentroid(),
        // Scroll delta always seems to be either 1f or -1f depending on the direction.
        // Although some mice are capable of sending precise scrolls, I'm assuming
        // Android coerces them to be at least (+/-)1f.
        zoomFactor = ShortcutEvent.DefaultZoomFactor * scrollY.absoluteValue,
      )
    }
  }

  private fun PointerEvent.calculateScroll(): Offset {
    return changes.fastFold(Offset.Zero) { acc, c ->
      acc + c.scrollDelta
    }
  }

  private fun PointerEvent.calculateScrollCentroid(): Offset {
    check(type == PointerEventType.Scroll)
    var centroid = Offset.Zero
    var centroidWeight = 0f
    changes.fastForEach { change ->
      val position = change.position
      centroid += position
      centroidWeight++
    }
    return when (centroidWeight) {
      0f -> Offset.Unspecified
      else -> centroid / centroidWeight
    }
  }
}
