package org.thoughtcrime.securesms.database

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.signal.core.util.delete
import org.signal.core.util.forEach
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.crypto.DatabaseSecret
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader.load
import org.thoughtcrime.securesms.database.model.MegaphoneRecord
import org.thoughtcrime.securesms.megaphone.Megaphones
import kotlin.concurrent.Volatile

/**
 * IMPORTANT: Writes should only be made through [org.thoughtcrime.securesms.megaphone.MegaphoneRepository].
 */
class MegaphoneDatabase(
  application: Application,
  databaseSecret: DatabaseSecret
) : SQLiteOpenHelper(
  application,
  DATABASE_NAME,
  databaseSecret.asString(),
  null,
  DATABASE_VERSION,
  0,
  SqlCipherErrorHandler(application, DATABASE_NAME),
  SqlCipherDatabaseHook(),
  true
),
  SignalDatabaseOpenHelper {

  companion object {
    private val TAG = tag(MegaphoneDatabase::class.java)

    private const val DATABASE_VERSION = 1
    private const val DATABASE_NAME = "signal-megaphone.db"

    private const val TABLE_NAME = "megaphone"
    private const val ID = "_id"
    private const val EVENT = "event"
    private const val INTERACTION_COUNT = "seen_count"
    private const val LAST_INTERACTION_TIMESTAMP = "last_seen"
    private const val FIRST_VISIBLE = "first_visible"
    private const val FINISHED = "finished"

    const val CREATE_TABLE: String = """CREATE TABLE $TABLE_NAME(
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $EVENT TEXT UNIQUE,
        $INTERACTION_COUNT INTEGER,
        $LAST_INTERACTION_TIMESTAMP INTEGER,
        $FIRST_VISIBLE INTEGER,
        $FINISHED INTEGER
    )"""

    @Volatile
    private var instance: MegaphoneDatabase? = null

    @JvmStatic
    fun getInstance(context: Application): MegaphoneDatabase {
      if (instance == null) {
        synchronized(MegaphoneDatabase::class.java) {
          if (instance == null) {
            load()
            instance = MegaphoneDatabase(context, DatabaseSecretProvider.getOrCreateDatabaseSecret(context))
          }
        }
      }
      return instance!!
    }
  }

  override fun onCreate(db: SQLiteDatabase) {
    Log.i(TAG, "onCreate()")
    db.execSQL(CREATE_TABLE)
  }

  override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    Log.i(TAG, "onUpgrade($oldVersion, $newVersion)")
  }

  override fun onOpen(db: SQLiteDatabase) {
    Log.i(TAG, "onOpen()")
    db.setForeignKeyConstraintsEnabled(true)
  }

  fun insert(events: Collection<Megaphones.Event>) {
    writableDatabase.withinTransaction { db ->
      for (event in events) {
        db.insertInto(TABLE_NAME)
          .values(EVENT to event.key)
          .run(SQLiteDatabase.CONFLICT_IGNORE)
      }
    }
  }

  fun getAllAndDeleteMissing(): MutableList<MegaphoneRecord> {
    val records: MutableList<MegaphoneRecord> = mutableListOf()

    writableDatabase.withinTransaction { db ->
      val missingKeys: MutableSet<String> = mutableSetOf()

      db.select()
        .from(TABLE_NAME)
        .run()
        .forEach { cursor ->
          val event = cursor.requireNonNullString(EVENT)
          val interactionCount = cursor.requireInt(INTERACTION_COUNT)
          val lastInteractionTime = cursor.requireLong(LAST_INTERACTION_TIMESTAMP)
          val firstVisible = cursor.requireLong(FIRST_VISIBLE)
          val finished = cursor.requireBoolean(FINISHED)

          if (Megaphones.Event.hasKey(event)) {
            records += MegaphoneRecord(
              event = Megaphones.Event.fromKey(event),
              interactionCount = interactionCount,
              lastInteractionTime = lastInteractionTime,
              firstVisible = firstVisible,
              finished = finished
            )
          } else {
            Log.w(TAG, "No in-app handing for event '$event'! Deleting it from the database.")
            missingKeys += event
          }
        }

      for (missing in missingKeys) {
        db.delete(TABLE_NAME)
          .where("$EVENT = ?", missing)
          .run()
      }
    }

    return records
  }

  fun markFirstVisible(event: Megaphones.Event, time: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(FIRST_VISIBLE to time)
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun markInteractedWith(event: Megaphones.Event, interactionCount: Int, lastInteractionTimestamp: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        INTERACTION_COUNT to interactionCount,
        LAST_INTERACTION_TIMESTAMP to lastInteractionTimestamp
      )
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun markFinished(event: Megaphones.Event) {
    writableDatabase
      .update(TABLE_NAME)
      .values(FINISHED to 1)
      .where("$EVENT = ?", event.key)
      .run()
  }

  fun delete(event: Megaphones.Event) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$EVENT = ?", event.key)
      .run()
  }

  override fun getSqlCipherDatabase(): SQLiteDatabase {
    return writableDatabase
  }
}
