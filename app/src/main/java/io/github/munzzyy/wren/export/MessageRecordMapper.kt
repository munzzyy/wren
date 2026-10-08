// Copyright 2026 Cole Munz
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.munzzyy.wren.export

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MentionUtil
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.longmessage.readFullBody
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * Turns a [MessageRecord] that already carries its attachments and reactions into an [ExportMessage].
 * Attachment files are handed to [exportAttachment], which decides where (and whether) the bytes go.
 */
class MessageRecordMapper(
  private val context: Context,
  private val exportAttachment: (messageId: Long, index: Int, attachment: DatabaseAttachment) -> ExportAttachment
) {

  private val you: String = context.getString(R.string.MessageRecord_you)
  private val names = HashMap<RecipientId, String>()

  @WorkerThread
  fun map(record: MessageRecord, mentions: List<Mention>): ExportMessage {
    val mms = record as? MmsMessageRecord
    val system = record.isUpdate
    val hidden = record.isRemoteDelete || record.isViewOnce

    val body = when {
      system -> record.getUpdateDisplayBody(context, null)?.spannable?.toString() ?: record.getDisplayBody(context).toString()
      hidden -> ""
      else -> resolveBody(record, mms, mentions)
    }

    val attachments = if (hidden || system || mms == null) {
      emptyList()
    } else {
      mms.slideDeck.slides
        .filterNot { MediaUtil.isLongTextType(it.contentType) }
        .mapNotNull { it.asAttachment() as? DatabaseAttachment }
        .mapIndexed { index, attachment -> exportAttachment(record.id, index + 1, attachment) }
    }

    val quote = mms?.quote?.takeUnless { hidden }?.let {
      ExportQuote(author = nameOf(it.author), text = it.displayText?.toString().orEmpty())
    }

    val links = if (hidden || mms == null) {
      emptyList()
    } else {
      mms.linkPreviews.map { ExportLink(title = it.title, url = it.url) }
    }

    return ExportMessage(
      id = record.id,
      sentAtMillis = record.dateSent,
      receivedAtMillis = record.dateReceived,
      sender = if (record.isOutgoing) you else nameOf(record.fromRecipient.id),
      outgoing = record.isOutgoing,
      body = body,
      system = system,
      remoteDeleted = record.isRemoteDelete,
      viewOnce = record.isViewOnce,
      expiresInMillis = record.expiresIn,
      quote = quote,
      reactions = record.reactions.map { ExportReaction(emoji = it.emoji, author = nameOf(it.author)) },
      attachments = attachments,
      linkPreviews = links
    )
  }

  private fun resolveBody(record: MessageRecord, mms: MmsMessageRecord?, mentions: List<Mention>): String {
    val longTextUri = mms?.slideDeck?.textSlide?.uri
    val raw: CharSequence = longTextUri
      ?.let { readFullBody(context, it) }
      ?.takeIf { it.isNotEmpty() }
      ?: record.getDisplayBody(context)

    if (mentions.isEmpty()) {
      return raw.toString()
    }

    return MentionUtil.updateBodyAndMentionsWithDisplayNames(context, raw, mentions).body?.toString() ?: raw.toString()
  }

  private fun nameOf(id: RecipientId): String {
    return names.getOrPut(id) {
      val recipient = Recipient.resolved(id)
      if (recipient.isSelf) you else recipient.getDisplayName(context)
    }
  }
}
