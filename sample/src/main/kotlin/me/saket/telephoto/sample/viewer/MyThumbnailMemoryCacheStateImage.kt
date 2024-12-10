/*
 * Copyright (C) 2024 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.saket.telephoto.sample.viewer;

import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.MemoryCache
import com.github.panpf.sketch.cache.getImageInfo
import com.github.panpf.sketch.cache.getTransformeds
import com.github.panpf.sketch.decode.internal.isInSampledTransformed
import com.github.panpf.sketch.state.StateImage;
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Find a Bitmap with the same aspect ratio and not modified by Transformation as a status image from memory
 * @param uri The uri of the image, if null use ImageRequest.uri
 *
 */


internal fun Float.format(newScale: Int): Float {
  return if (this.isNaN()) {
    this
  } else {
    val multiplier = 10.0.pow(newScale)
    (round(this * multiplier) / multiplier).toFloat()
  }
}


data class MyThumbnailMemoryCacheStateImage(
    val uri: String? = null,
    val defaultImage:StateImage? = null
) : StateImage {

    override val key: String =
        "ThumbnailMemoryCache(${uri?.let { "'${it}'" }},${defaultImage?.key})"

    override fun getImage(
      sketch: Sketch,
      request: com.github.panpf.sketch.request.ImageRequest,
      throwable: Throwable?
    ): com.github.panpf.sketch.Image? {
        val uri: String = uri ?: request.uri.toString()
        val keys = sketch.memoryCache.keys()
      println("hmtz: " + keys)
        var targetCachedValue: MemoryCache.Value? = null
        var count = 0
        for (key in keys) {
            // The key is spliced by uri and options. The options start with '_'. See RequestUtils.newKey() for details.
            var paramsStartFlagIndex = key.indexOf("?_")
            if (paramsStartFlagIndex == -1) {
                paramsStartFlagIndex = key.indexOf("&_")
            }
            val uriFromKey = if (paramsStartFlagIndex != -1) {
                key.substring(startIndex = 0, endIndex = paramsStartFlagIndex)
            } else {
                key
            }
            if (uri == uriFromKey) {
                val cachedValue = sketch.memoryCache[key]?.takeIf {
                    val image = it.image
                    val bitmapAspectRatio = (image.width.toFloat() / image.height).format(1)
                    val imageInfo = it.getImageInfo()!!
                    val imageAspectRatio =
                        (imageInfo.width.toFloat() / imageInfo.height).format(1)
                    val sizeSame = abs(bitmapAspectRatio - imageAspectRatio) <= 0.1f

                    val transformeds = it.getTransformeds()
                    val noOtherTransformed =
                        transformeds == null || transformeds.all { transformed ->
                            isInSampledTransformed(transformed)
                        }

                    sizeSame && noOtherTransformed
                }
                if (cachedValue != null) {
                    targetCachedValue = cachedValue
                    break
                } else if (++count >= 3) {
                    break
                }
            }
        }
        return targetCachedValue?.image ?: defaultImage?.getImage(sketch, request, throwable)
    }

    override fun toString(): String =
        "ThumbnailMemoryCacheStateImage(uri=${uri?.let { "'${it}'" }}, defaultImage=$defaultImage)"
}
