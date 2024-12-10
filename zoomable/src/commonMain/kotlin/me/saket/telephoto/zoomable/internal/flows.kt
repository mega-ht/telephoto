package me.saket.telephoto.zoomable.internal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun <T, R> Flow<T>.zipWithPrevious(
  mapper: (previous: T, current: T) -> R,
): Flow<R> = flow {
  // Mutex locking isn't needed for UI, which is single threaded.
  var previousValue: T? = null
  collect { currentValue ->
    previousValue?.let { previousValue ->
      emit(mapper(previousValue, currentValue))
    }
    previousValue = currentValue
  }
}
