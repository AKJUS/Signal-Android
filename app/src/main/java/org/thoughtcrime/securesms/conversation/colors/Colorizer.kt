package org.thoughtcrime.securesms.conversation.colors

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.signal.core.models.ServiceId
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.signal.core.ui.R as CoreUiR

/**
 * Helper class for all things ChatColors.
 *
 * - Maintains a mapping for group recipient colors
 * - Gives easy access to different bubble colors
 * - Watches and responds to RecyclerView scroll and layout changes to update a ColorizerView
 */
class Colorizer @JvmOverloads constructor(groupMemberIds: List<ServiceId> = emptyList()) {

  private var colorsHaveBeenSet = false

  @Deprecated("Not needed for CFv2")
  private val groupSenderColors: MutableMap<RecipientId, NameColor> = mutableMapOf()

  private val groupMembers: LinkedHashSet<ServiceId> = linkedSetOf()

  init {
    onGroupMembershipChanged(groupMemberIds)
  }

  @ColorInt
  fun getOutgoingBodyTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_outgoing_body_color)
  }

  @ColorInt
  fun getOutgoingFooterTextColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_outgoing_footer_color)
  }

  @ColorInt
  fun getOutgoingFooterIconColor(context: Context): Int {
    return ContextCompat.getColor(context, R.color.conversation_outgoing_footer_color)
  }

  @ColorInt
  fun getIncomingBodyTextColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorNeutralInverse)
    } else {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurface)
    }
  }

  @ColorInt
  fun getIncomingFooterTextColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorNeutralVariantInverse)
    } else {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant)
    }
  }

  @ColorInt
  fun getIncomingFooterIconColor(context: Context, hasWallpaper: Boolean): Int {
    return if (hasWallpaper) {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorNeutralVariantInverse)
    } else {
      ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant)
    }
  }

  @ColorInt
  fun getIncomingGroupSenderColor(context: Context, recipient: Recipient): Int {
    return getNameColor(context, recipient).getColor(context)
  }

  fun onGroupMembershipChanged(serviceIds: List<ServiceId>) {
    groupMembers.addAll(serviceIds.sortedBy { it.toString() })
  }

  @Suppress("DEPRECATION")
  fun getNameColor(context: Context, recipient: Recipient): NameColor {
    if (groupMembers.isEmpty()) {
      return groupSenderColors[recipient.id] ?: getDefaultColor(context, recipient)
    }

    val serviceId = recipient.serviceId.orNull()
    if (serviceId != null) {
      val position = groupMembers.indexOf(serviceId)
      if (position >= 0) {
        return ChatColorsPalette.Names.all[position % ChatColorsPalette.Names.all.size]
      }
    }
    return getDefaultColor(context, recipient)
  }

  @Suppress("DEPRECATION")
  @Deprecated("Not needed for CFv2", ReplaceWith("onGroupMembershipChanged"))
  fun onNameColorsChanged(nameColorMap: Map<RecipientId, NameColor>) {
    groupSenderColors.clear()
    groupSenderColors.putAll(nameColorMap)
    colorsHaveBeenSet = true
  }

  @Suppress("DEPRECATION")
  private fun getDefaultColor(context: Context, recipient: Recipient): NameColor {
    return if (colorsHaveBeenSet) {
      ChatColorsPalette.Names.all[groupSenderColors.size % ChatColorsPalette.Names.all.size]
        .also { groupSenderColors[recipient.id] = it }
    } else {
      val colorInt = getIncomingBodyTextColor(context, recipient.hasWallpaper)
      NameColor(lightColor = colorInt, darkColor = colorInt)
    }
  }
}
