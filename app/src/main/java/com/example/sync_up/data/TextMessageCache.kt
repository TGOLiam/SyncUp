package com.example.sync_up.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import com.example.sync_up.data.remote.TextMessage

/** Reads and writes only server-confirmed messages. Call from a worker thread. */
internal class TextMessageCache(
    context: Context,
    databaseName: String = SyncUpDatabaseHelper.DATABASE_NAME
) : AutoCloseable {
    private val helper = SyncUpDatabaseHelper(context, databaseName)

    fun save(userId: String, messages: List<TextMessage>) {
        check(Looper.myLooper() != Looper.getMainLooper())
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            for (message in messages) {
                database.insertWithOnConflict("text_messages", null, ContentValues().apply {
                    put("user_id", userId)
                    put("message_id", message.id)
                    put("channel_id", message.channelId)
                    put("sender_member_id", message.senderMemberId)
                    put("text_content", message.text)
                    put("sent_at", message.sentAt)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun recent(userId: String, channelId: String): List<TextMessage> {
        check(Looper.myLooper() != Looper.getMainLooper())
        val messages = mutableListOf<TextMessage>()
        helper.readableDatabase.query(
            "text_messages",
            arrayOf("message_id", "channel_id", "sender_member_id", "text_content", "sent_at"),
            "user_id = ? AND channel_id = ?",
            arrayOf(userId, channelId), null, null, "sent_at DESC, message_id DESC", "20"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(TextMessage(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4)))
            }
        }
        return messages.asReversed()
    }

    override fun close() = helper.close()
}
