/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

fun Context.getDownloadManager(): DownloadManager {
  return this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
}

fun Context.safeUnregisterReceiver(receiver: BroadcastReceiver?) {
  if (receiver == null) return

  try {
    unregisterReceiver(receiver)
  } catch (_: IllegalArgumentException) {
  }
}

/**
 * Attempts to resolve a [FragmentActivity] from this context. Throws an [IllegalStateException] if none is found.
 */
fun Context.requireFragmentActivity(): FragmentActivity {
  return resolveFragmentActivity() ?: throw IllegalStateException("Required FragmentActivity context, but found: ${javaClass.name} instead.")
}

/**
 * Attempts to resolve a [FragmentActivity] from this context. Returns null for non-activity contexts.
 */
fun Context.resolveFragmentActivity(): FragmentActivity? {
  return when (val context = this) {
    is FragmentActivity -> context
    is ContextWrapper -> context.baseContext.takeUnless { it === this }?.resolveFragmentActivity()
    else -> null
  }
}
