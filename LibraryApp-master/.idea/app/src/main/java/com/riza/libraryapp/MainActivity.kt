package com.riza.libraryapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var db: DatabaseHelper
    private lateinit var tvHasil: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        tvHasil = findViewById(R.id.tvHasil)

        findViewById<Button>(R.id.btnTambahData).setOnClickListener {
            db.insertBuku("Android Studio 101", "Dosen IT", 5)
            db.insertAnggota("Andi", "andi@test.com")
            refreshUI()
            Toast.makeText(this, "Data Sampel Ditambahkan", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPinjam).setOnClickListener {
            val daftarBuku = db.getAllBuku()
            if (daftarBuku.isNotEmpty()) {
                val idBukuTersedia = daftarBuku[0].id
                val sukses = db.prosesPeminjaman(idBukuTersedia, 1)

                if (sukses) {
                    refreshUI()
                } else {
                    Toast.makeText(this, "Gagal: Stok Habis!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Tambah data buku dulu!", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnKembali).setOnClickListener {
            val sukses = db.prosesPengembalianDinamis()
            if (sukses) {
                refreshUI()
            } else {
                Toast.makeText(this, "Tidak ada buku yang perlu dikembalikan", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            val d = db.writableDatabase
            d.execSQL("DELETE FROM ${DatabaseHelper.TABLE_PEMINJAMAN}")
            d.execSQL("DELETE FROM ${DatabaseHelper.TABLE_BUKU}")
            d.execSQL("DELETE FROM ${DatabaseHelper.TABLE_ANGGOTA}")
            d.execSQL("DELETE FROM sqlite_sequence WHERE name='${DatabaseHelper.TABLE_BUKU}'")
            d.close()
            refreshUI()
            Toast.makeText(this, "Database Kosong", Toast.LENGTH_SHORT).show()
        }

        refreshUI()
    }

    private fun refreshUI() {
        val sb = StringBuilder()

        val stat = db.getDashboardStats()
        sb.append("=== DASHBOARD STATISTIK ===\n")
        sb.append("Total Judul : ${stat.totalBuku}\n")
        sb.append("Total Member: ${stat.totalAnggota}\n")
        sb.append("Sedang Pinjam: ${stat.sedangPinjam}\n")
        sb.append("Total Stok  : ${stat.totalStok}\n\n")

        sb.append("=== LAPORAN PINJAM AKTIF ===\n")
        val cursor = db.getLaporanPeminjaman()
        if (cursor.count == 0) {
            sb.append("(Kosong)\n")
        } else {
            cursor.use { cur ->
                while (cur.moveToNext()) {
                    val idPinjam = cur.getInt(0)
                    val judul = cur.getString(1)
                    val nama = cur.getString(2)
                    sb.append("- ID:$idPinjam | $nama pinjam [$judul]\n")
                }
            }
        }

        tvHasil.text = sb.toString()
    }
}
