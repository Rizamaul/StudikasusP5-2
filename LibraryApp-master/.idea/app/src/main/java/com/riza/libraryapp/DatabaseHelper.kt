package com.riza.libraryapp

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Buku(val id: Int, val judul: String, val pengarang: String, val stok: Int)
data class Anggota(val id: Int, val nama: String, val email: String)
data class DashboardStat(val totalBuku: Int, val totalAnggota: Int, val sedangPinjam: Int, val totalStok: Int)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "library.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_BUKU = "buku"
        const val TABLE_ANGGOTA = "anggota"
        const val TABLE_PEMINJAMAN = "peminjaman"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_BUKU (id INTEGER PRIMARY KEY AUTOINCREMENT, judul TEXT, pengarang TEXT, stok INTEGER)")
        db.execSQL("CREATE TABLE $TABLE_ANGGOTA (id INTEGER PRIMARY KEY AUTOINCREMENT, nama TEXT, email TEXT)")
        db.execSQL("CREATE TABLE $TABLE_PEMINJAMAN (id INTEGER PRIMARY KEY AUTOINCREMENT, buku_id INTEGER, anggota_id INTEGER, status TEXT DEFAULT 'dipinjam', tgl_pinjam TEXT DEFAULT (date('now')), tgl_kembali TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BUKU")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ANGGOTA")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PEMINJAMAN")
        onCreate(db)
    }

    fun insertBuku(judul: String, pengarang: String, stok: Int): Long {
        val db = writableDatabase
        val v = ContentValues().apply {
            put("judul", judul); put("pengarang", pengarang); put("stok", stok)
        }
        return db.insert(TABLE_BUKU, null, v)
    }

    fun getAllBuku(): List<Buku> {
        val list = mutableListOf<Buku>()
        readableDatabase.rawQuery("SELECT * FROM $TABLE_BUKU", null).use { cur ->
            while (cur.moveToNext()) {
                list.add(Buku(cur.getInt(0), cur.getString(1), cur.getString(2), cur.getInt(3)))
            }
        }
        return list
    }

    fun updateBuku(id: Int, judul: String, pengarang: String, stok: Int): Int {
        val v = ContentValues().apply {
            put("judul", judul); put("pengarang", pengarang); put("stok", stok)
        }
        return writableDatabase.update(TABLE_BUKU, v, "id=?", arrayOf(id.toString()))
    }

    fun deleteBuku(id: Int) = writableDatabase.delete(TABLE_BUKU, "id=?", arrayOf(id.toString()))

    fun insertAnggota(nama: String, email: String): Long {
        val v = ContentValues().apply { put("nama", nama); put("email", email) }
        return writableDatabase.insert(TABLE_ANGGOTA, null, v)
    }

    fun prosesPeminjaman(bukuId: Int, anggotaId: Int): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val v = ContentValues().apply {
                put("buku_id", bukuId)
                put("anggota_id", anggotaId)
            }
            db.insert(TABLE_PEMINJAMAN, null, v)
            db.execSQL("UPDATE $TABLE_BUKU SET stok = stok - 1 WHERE id = ? AND stok > 0", arrayOf(bukuId))
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) { false } finally {
            db.endTransaction()
        }
    }

    fun prosesPengembalian(pinjamId: Int, bukuId: Int): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val v = ContentValues().apply {
                put("status", "dikembalikan")
                put("tgl_kembali", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
            }
            db.update(TABLE_PEMINJAMAN, v, "id=?", arrayOf(pinjamId.toString()))

            db.execSQL("UPDATE $TABLE_BUKU SET stok = stok + 1 WHERE id = ?", arrayOf(bukuId))

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) { false } finally {
            db.endTransaction()
        }
    }

    fun prosesPengembalianDinamis(): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val cursor = db.rawQuery("SELECT id, buku_id FROM $TABLE_PEMINJAMAN WHERE status = 'dipinjam' LIMIT 1", null)
            if (cursor.moveToFirst()) {
                val pinjamId = cursor.getInt(0)
                val bukuId = cursor.getInt(1)
                cursor.close()

                val v = ContentValues().apply {
                    put("status", "dikembalikan")
                    put("tgl_kembali", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                }
                db.update(TABLE_PEMINJAMAN, v, "id=?", arrayOf(pinjamId.toString()))
                db.execSQL("UPDATE $TABLE_BUKU SET stok = stok + 1 WHERE id = ?", arrayOf(bukuId))

                db.setTransactionSuccessful()
                true
            } else {
                cursor.close()
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            db.endTransaction()
        }
    }

    fun getDashboardStats(): DashboardStat {
        val sql = """
            SELECT 
                (SELECT COUNT(*) FROM $TABLE_BUKU) as total_buku,
                (SELECT COUNT(*) FROM $TABLE_ANGGOTA) as total_anggota,
                (SELECT COUNT(*) FROM $TABLE_PEMINJAMAN WHERE status='dipinjam') as sedang_pinjam,
                (SELECT SUM(stok) FROM $TABLE_BUKU) as total_stok
        """.trimIndent()

        readableDatabase.rawQuery(sql, null).use { cur ->
            if (cur.moveToFirst()) {
                return DashboardStat(cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3))
            }
        }
        return DashboardStat(0, 0, 0, 0)
    }

    fun getLaporanPeminjaman(): Cursor {
        val sql = """
            SELECT p.id, b.judul, a.nama, p.tgl_pinjam, 
            (julianday('now') - julianday(p.tgl_pinjam)) AS hari_pinjam
            FROM $TABLE_PEMINJAMAN p
            INNER JOIN $TABLE_BUKU b ON p.buku_id = b.id
            INNER JOIN $TABLE_ANGGOTA a ON p.anggota_id = a.id
            WHERE p.status = 'dipinjam'
            ORDER BY p.tgl_pinjam ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null)
    }
}
