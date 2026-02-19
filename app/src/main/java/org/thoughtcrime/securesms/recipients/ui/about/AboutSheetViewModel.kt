/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.Optional

class AboutSheetViewModel(
  recipientId: RecipientId,
  groupId: GroupId.V2? = null,
  private val repository: AboutSheetRepository = AboutSheetRepository()
) : ViewModel() {

  private val _recipient: MutableState<Optional<Recipient>> = mutableStateOf(Optional.empty())
  val recipient: State<Optional<Recipient>> = _recipient

  private val _groupsInCommonCount: MutableIntState = mutableIntStateOf(0)
  val groupsInCommonCount: IntState = _groupsInCommonCount

  private val _verified: MutableState<Boolean> = mutableStateOf(false)
  val verified: State<Boolean> = _verified

  private val _memberLabel: MutableState<MemberLabel?> = mutableStateOf(null)
  val memberLabel: State<MemberLabel?> = _memberLabel

  private val _canEditMemberLabel: MutableState<Boolean> = mutableStateOf(false)
  val canEditMemberLabel: State<Boolean> = _canEditMemberLabel

  private val disposables = CompositeDisposable()

  private val recipientDisposable: Disposable = Recipient
    .observable(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy {
      _recipient.value = Optional.of(it)
    }

  private val groupsInCommonDisposable: Disposable = repository
    .getGroupsInCommonCount(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy {
      _groupsInCommonCount.intValue = it
    }

  private val verifiedDisposable: Disposable = repository
    .getVerified(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy {
      _verified.value = it
    }

  init {
    disposables.addAll(recipientDisposable, groupsInCommonDisposable, verifiedDisposable)

    if (groupId != null && RemoteConfig.sendMemberLabels) {
      observeMemberLabel(groupId)
    }
  }

  private fun observeMemberLabel(groupId: GroupId.V2) {
    disposables.add(
      repository.getMemberLabel(groupId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy { _memberLabel.value = it.orElse(null) }
    )

    disposables.add(
      repository.canEditMemberLabel(groupId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy { _canEditMemberLabel.value = it }
    )
  }

  override fun onCleared() {
    disposables.dispose()
  }
}
