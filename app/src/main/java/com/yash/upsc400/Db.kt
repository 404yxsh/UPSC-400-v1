package com.yash.upsc400

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class Session(val id: Long, val slotKey: String, val kind: String, val startMs: Long, val endMs: Long?, val durMs: Long, val manual: Boolean)
class Note(val id: Long, val slotKey: String, val title: String, val body: String, val updatedMs: Long)

/** Local SQLite storage. The timer stores timestamps only, so it survives screen lock, app close and process death. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "upsc400.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("CREATE TABLE slot_state(slot_key TEXT PRIMARY KEY, done_at INTEGER, problems INTEGER)")
        db.execSQL("CREATE TABLE session(id INTEGER PRIMARY KEY AUTOINCREMENT, slot_key TEXT, kind TEXT, start_ms INTEGER, end_ms INTEGER, dur_ms INTEGER, manual INTEGER DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_session_slot ON session(slot_key)")
        db.execSQL("CREATE TABLE link(item_id TEXT PRIMARY KEY, url TEXT)")
        db.execSQL("CREATE TABLE note(id INTEGER PRIMARY KEY AUTOINCREMENT, slot_key TEXT, title TEXT, body TEXT, updated_ms INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---- settings
    fun setting(k: String, def: String): String {
        readableDatabase.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k)).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return def
    }

    fun putSetting(k: String, v: String) {
        val cv = ContentValues(); cv.put("k", k); cv.put("v", v)
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- slot completion
    fun doneKeys(): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT slot_key FROM slot_state WHERE done_at IS NOT NULL", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun problemsDone(key: String): Int {
        readableDatabase.rawQuery("SELECT problems FROM slot_state WHERE slot_key=?", arrayOf(key)).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getInt(0)
        }
        return 0
    }

    fun markDone(key: String, now: Long, problems: Int) {
        val cv = ContentValues(); cv.put("slot_key", key); cv.put("done_at", now); cv.put("problems", problems)
        writableDatabase.insertWithOnConflict("slot_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun reopen(key: String) { writableDatabase.delete("slot_state", "slot_key=?", arrayOf(key)) }

    fun doneProblemsAll(): Map<String, Int> {
        val out = HashMap<String, Int>()
        readableDatabase.rawQuery("SELECT slot_key, problems FROM slot_state WHERE done_at IS NOT NULL", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = if (c.isNull(1)) 0 else c.getInt(1)
        }
        return out
    }

    // ---- timer sessions
    fun activeSession(): Session? {
        readableDatabase.rawQuery("SELECT id, slot_key, kind, start_ms, end_ms, dur_ms, manual FROM session WHERE end_ms IS NULL AND manual=0 ORDER BY id DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) return rowToSession(c)
        }
        return null
    }

    private fun rowToSession(c: android.database.Cursor): Session =
        Session(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), if (c.isNull(4)) null else c.getLong(4), if (c.isNull(5)) 0L else c.getLong(5), c.getInt(6) == 1)

    /** Ends the running session (if any) at now. */
    fun pauseActive(now: Long) {
        val a = activeSession() ?: return
        val cv = ContentValues(); cv.put("end_ms", now); cv.put("dur_ms", now - a.startMs)
        writableDatabase.update("session", cv, "id=?", arrayOf(a.id.toString()))
    }

    /** Starts a session; any other running timer is paused first, so only one timer is ever active. */
    fun startSession(slotKey: String, kind: String, now: Long) {
        pauseActive(now)
        val cv = ContentValues(); cv.put("slot_key", slotKey); cv.put("kind", kind); cv.put("start_ms", now); cv.putNull("end_ms"); cv.put("dur_ms", 0); cv.put("manual", 0)
        writableDatabase.insert("session", null, cv)
    }

    /** Manual add (positive) or reduction (negative) of logged time for a slot. */
    fun addManual(slotKey: String, kind: String, deltaMs: Long, now: Long) {
        val cv = ContentValues(); cv.put("slot_key", slotKey); cv.put("kind", kind); cv.put("start_ms", now); cv.put("end_ms", now); cv.put("dur_ms", deltaMs); cv.put("manual", 1)
        writableDatabase.insert("session", null, cv)
    }

    fun elapsedMs(slotKey: String, now: Long): Long {
        var total = 0L
        readableDatabase.rawQuery("SELECT end_ms, start_ms, dur_ms, manual FROM session WHERE slot_key=?", arrayOf(slotKey)).use { c ->
            while (c.moveToNext()) {
                total += if (c.isNull(0) && c.getInt(3) == 0) (now - c.getLong(1)) else c.getLong(2)
            }
        }
        return if (total < 0) 0 else total
    }

    fun allSessions(): List<Session> {
        val out = ArrayList<Session>()
        readableDatabase.rawQuery("SELECT id, slot_key, kind, start_ms, end_ms, dur_ms, manual FROM session ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(rowToSession(c))
        }
        return out
    }

    fun sessionsForSlot(slotKey: String): List<Session> = allSessions().filter { it.slotKey == slotKey }

    // ---- lecture links
    fun link(itemId: String): String {
        readableDatabase.rawQuery("SELECT url FROM link WHERE item_id=?", arrayOf(itemId)).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return ""
    }

    fun putLink(itemId: String, url: String) {
        if (url.isBlank()) { writableDatabase.delete("link", "item_id=?", arrayOf(itemId)); return }
        val cv = ContentValues(); cv.put("item_id", itemId); cv.put("url", url.trim())
        writableDatabase.insertWithOnConflict("link", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- notes
    fun notes(): List<Note> {
        val out = ArrayList<Note>()
        readableDatabase.rawQuery("SELECT id, slot_key, title, body, updated_ms FROM note ORDER BY updated_ms DESC", null).use { c ->
            while (c.moveToNext()) out.add(Note(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(4)))
        }
        return out
    }

    fun saveNote(id: Long, slotKey: String, title: String, body: String, now: Long) {
        val cv = ContentValues(); cv.put("slot_key", slotKey); cv.put("title", title); cv.put("body", body); cv.put("updated_ms", now)
        if (id > 0) writableDatabase.update("note", cv, "id=?", arrayOf(id.toString())) else writableDatabase.insert("note", null, cv)
    }

    fun deleteNote(id: Long) { writableDatabase.delete("note", "id=?", arrayOf(id.toString())) }

    // ---- backup as JSON
    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "upsc400-backup"); root.put("version", 1)
        fun dump(table: String, cols: List<String>): JSONArray {
            val arr = JSONArray()
            readableDatabase.rawQuery("SELECT ${cols.joinToString(",")} FROM $table", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in cols.indices) {
                        if (c.isNull(i)) o.put(cols[i], JSONObject.NULL)
                        else when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> o.put(cols[i], c.getLong(i))
                            else -> o.put(cols[i], c.getString(i))
                        }
                    }
                    arr.put(o)
                }
            }
            return arr
        }
        root.put("settings", dump("settings", listOf("k", "v")))
        root.put("slot_state", dump("slot_state", listOf("slot_key", "done_at", "problems")))
        root.put("session", dump("session", listOf("slot_key", "kind", "start_ms", "end_ms", "dur_ms", "manual")))
        root.put("link", dump("link", listOf("item_id", "url")))
        root.put("note", dump("note", listOf("slot_key", "title", "body", "updated_ms")))
        return root.toString(1)
    }

    /** Replaces all stored data with the backup. Throws if the file is not a valid backup. */
    fun importJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "upsc400-backup") { "Not an UPSC 400 backup file" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in listOf("settings", "slot_state", "session", "link", "note")) db.delete(t, null, null)
            fun load(table: String, cols: List<String>) {
                val arr = root.optJSONArray(table) ?: return
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    for (c in cols) {
                        if (o.isNull(c)) cv.putNull(c)
                        else {
                            val v = o.get(c)
                            if (v is Number) cv.put(c, v.toLong()) else cv.put(c, v.toString())
                        }
                    }
                    db.insert(table, null, cv)
                }
            }
            load("settings", listOf("k", "v"))
            load("slot_state", listOf("slot_key", "done_at", "problems"))
            load("session", listOf("slot_key", "kind", "start_ms", "end_ms", "dur_ms", "manual"))
            load("link", listOf("item_id", "url"))
            load("note", listOf("slot_key", "title", "body", "updated_ms"))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
