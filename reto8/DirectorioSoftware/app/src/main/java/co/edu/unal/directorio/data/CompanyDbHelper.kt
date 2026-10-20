package co.edu.unal.directorio.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Nombres de tabla y columnas, centralizados para evitar errores de tipeo en las consultas. */
internal object CompanyTable {
    const val NAME = "companies"
    const val ID = "_id"
    const val COMPANY_NAME = "name"
    const val WEBSITE = "website"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val PRODUCTS = "products_services"
    const val CLASSIFICATION = "classification"
}

/** Crea y versiona la base de datos SQLite (la misma API del tutorial del reto). */
class CompanyDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ${CompanyTable.NAME} (
                ${CompanyTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${CompanyTable.COMPANY_NAME} TEXT NOT NULL,
                ${CompanyTable.WEBSITE} TEXT NOT NULL DEFAULT '',
                ${CompanyTable.PHONE} TEXT NOT NULL DEFAULT '',
                ${CompanyTable.EMAIL} TEXT NOT NULL DEFAULT '',
                ${CompanyTable.PRODUCTS} TEXT NOT NULL DEFAULT '',
                ${CompanyTable.CLASSIFICATION} INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_companies_name ON ${CompanyTable.NAME}(${CompanyTable.COMPANY_NAME} COLLATE NOCASE)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 1: sin migraciones. Si el esquema cambia, agregar aquí ALTER TABLE incrementales
        // en lugar de borrar la tabla, para no perder los datos del usuario.
    }

    private companion object {
        const val DB_NAME = "directorio.db"
        const val DB_VERSION = 1
    }
}