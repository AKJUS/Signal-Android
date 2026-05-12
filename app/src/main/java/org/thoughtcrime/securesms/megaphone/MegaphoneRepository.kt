package org.thoughtcrime.securesms.megaphone

import android.app.Application
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.MegaphoneDatabase
import org.thoughtcrime.securesms.database.model.MegaphoneRecord
import java.util.concurrent.Executor

/**
 * Synchronization of data structures is done using a serial executor. Do not access or change
 * data structures or fields on anything except the executor.
 */
class MegaphoneRepository(private val context: Application) {
  private val executor: Executor = SignalExecutors.SERIAL
  private val database: MegaphoneDatabase = MegaphoneDatabase.getInstance(context)
  private val databaseCache: MutableMap<Megaphones.Event?, MegaphoneRecord> = mutableMapOf()

  private var enabled = false

  init {
    executor.execute {
      this.init()
    }
  }

  /**
   * Marks any megaphones a new user shouldn't see as "finished".
   */
  @AnyThread
  fun onFirstEverAppLaunch() {
    executor.execute {
      database.markFinished(Megaphones.Event.ADD_A_PROFILE_PHOTO)
      database.markFinished(Megaphones.Event.PNP_LAUNCH)
      resetDatabaseCache()
    }
  }

  @AnyThread
  fun onAppForegrounded() {
    executor.execute {
      enabled = true
    }
  }

  @AnyThread
  fun getNextMegaphone(callback: Callback<Megaphone?>) {
    executor.execute {
      if (enabled) {
        init()
        callback.onResult(Megaphones.getNextMegaphone(context, databaseCache))
      } else {
        callback.onResult(null)
      }
    }
  }

  @AnyThread
  fun markVisible(event: Megaphones.Event) {
    val time = System.currentTimeMillis()

    executor.execute {
      if (getRecord(event).firstVisible == 0L) {
        database.markFirstVisible(event, time)
        resetDatabaseCache()
      }
    }
  }

  @AnyThread
  fun markInteractedWith(event: Megaphones.Event) {
    val currentTime = System.currentTimeMillis()

    executor.execute {
      val record = getRecord(event)
      database.markInteractedWith(event, record.interactionCount + 1, currentTime)
      enabled = false
      resetDatabaseCache()
    }
  }

  @AnyThread
  fun markFinished(event: Megaphones.Event) {
    markFinished(event, null)
  }

  @AnyThread
  fun markFinished(event: Megaphones.Event, onComplete: Runnable?) {
    executor.execute {
      val record = databaseCache[event]
      if (record != null && record.finished) {
        return@execute
      }

      database.markFinished(event)
      resetDatabaseCache()
      onComplete?.run()
    }
  }

  @WorkerThread
  private fun init() {
    val records: MutableList<MegaphoneRecord> = database.getAllAndDeleteMissing()
    val events = records.map { it.event }.toSet()
    val missing = Megaphones.Event.entries - events

    database.insert(missing)
    resetDatabaseCache()
  }

  @WorkerThread
  private fun getRecord(event: Megaphones.Event): MegaphoneRecord {
    return databaseCache.get(event)!!
  }

  @WorkerThread
  private fun resetDatabaseCache() {
    databaseCache.clear()
    databaseCache += database.getAllAndDeleteMissing().associateBy { it.event }
  }

  fun interface Callback<E> {
    fun onResult(result: E?)
  }
}
