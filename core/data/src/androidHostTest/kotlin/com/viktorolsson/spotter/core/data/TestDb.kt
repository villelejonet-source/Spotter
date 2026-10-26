package com.viktorolsson.spotter.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.viktorolsson.spotter.core.data.db.SpotterDatabase
import kotlinx.coroutines.runBlocking

/** An in-memory database set up exactly like the app's (driver, migrations, sync triggers). */
fun testDatabase(): SpotterDatabase =
    SpotterDatabase.configure(
        Room.inMemoryDatabaseBuilder<SpotterDatabase>(ApplicationProvider.getApplicationContext<Context>()),
        AndroidSQLiteDriver(),
    ).build()

/**
 * Raw SQL for tests, in the shape the tests were written against (SupportSQLiteDatabase and
 * Cursor): `db.sql.execSQL(…)`, `db.sql.query(…).use { it.moveToFirst(); it.getString(0) }`.
 */
val SpotterDatabase.sql: TestSql get() = TestSql(this)

class TestSql(private val db: SpotterDatabase) {
    fun execSQL(sql: String, bindArgs: Array<out Any?> = emptyArray()) = runBlocking {
        db.useWriterConnection { it.usePrepared(sql) { st -> st.bind(bindArgs); st.step() } }
        Unit
    }

    fun query(sql: String, bindArgs: Array<out Any?> = emptyArray()): TestCursor = runBlocking {
        db.useReaderConnection { it.usePrepared(sql) { st -> st.bind(bindArgs); TestCursor.read(st) } }
    }

    val version: Int get() = query("PRAGMA user_version").use { it.moveToFirst(); it.getInt(0) }
}

fun SQLiteConnection.query(sql: String): TestCursor = prepare(sql).use(TestCursor::read)

/** A result set read into memory, with the Cursor calls the tests use. */
class TestCursor private constructor(private val names: List<String>, private val rows: List<List<Any?>>) : AutoCloseable {
    private var position = -1

    fun moveToFirst(): Boolean { position = 0; return rows.isNotEmpty() }
    fun moveToNext(): Boolean = ++position < rows.size
    fun getColumnIndexOrThrow(name: String): Int = names.indexOf(name).also { require(it >= 0) { "No column $name" } }
    fun isNull(i: Int) = rows[position][i] == null
    fun getString(i: Int): String? = rows[position][i]?.toString()
    fun getLong(i: Int): Long = (rows[position][i] as Number).toLong()
    fun getInt(i: Int): Int = getLong(i).toInt()
    override fun close() = Unit

    companion object {
        fun read(st: SQLiteStatement): TestCursor {
            val names = List(st.getColumnCount()) { st.getColumnName(it) }
            val rows = buildList {
                while (st.step()) {
                    add(
                        List(names.size) { i ->
                            when {
                                st.isNull(i) -> null
                                st.getColumnType(i) == androidx.sqlite.SQLITE_DATA_INTEGER -> st.getLong(i)
                                st.getColumnType(i) == androidx.sqlite.SQLITE_DATA_FLOAT -> st.getDouble(i)
                                else -> st.getText(i)
                            }
                        },
                    )
                }
            }
            return TestCursor(names, rows)
        }
    }
}

private fun SQLiteStatement.bind(args: Array<out Any?>) = args.forEachIndexed { i, value ->
    when (value) {
        null -> bindNull(i + 1)
        is Long -> bindLong(i + 1, value)
        is Int -> bindLong(i + 1, value.toLong())
        is Double -> bindDouble(i + 1, value)
        is String -> bindText(i + 1, value)
        else -> error("Can't bind ${value::class.simpleName}")
    }
}
