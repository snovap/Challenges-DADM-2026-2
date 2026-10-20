package co.edu.unal.directorio.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import co.edu.unal.directorio.model.Classification
import co.edu.unal.directorio.model.Company
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Contrato CRUD. La capa de UI depende de esta interfaz, no de SQLite. */
interface CompanyRepository {
    /** Lista ordenada por nombre. Los filtros son opcionales y se combinan con AND. */
    suspend fun getAll(query: String = "", classification: Classification? = null): List<Company>
    suspend fun getById(id: Long): Company?
    suspend fun insert(company: Company): Long
    suspend fun update(company: Company): Boolean
    suspend fun delete(id: Long): Boolean
}

class SqliteCompanyRepository(context: Context) : CompanyRepository {

    private val helper = CompanyDbHelper(context)

    // Todo acceso a disco corre en Dispatchers.IO, nunca en el hilo principal.

    override suspend fun getAll(query: String, classification: Classification?): List<Company> =
        withContext(Dispatchers.IO) {
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            query.trim().takeIf { it.isNotEmpty() }?.let {
                conditions += "${CompanyTable.COMPANY_NAME} LIKE ? ESCAPE '\\'"
                args += "%${escapeLike(it)}%"
            }
            classification?.let {
                // 'bit' viene del enum (no del usuario), por eso es seguro interpolarlo.
                conditions += "(${CompanyTable.CLASSIFICATION} & ${it.bit}) != 0"
            }

            helper.readableDatabase.query(
                CompanyTable.NAME,
                null,
                conditions.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
                args.takeIf { it.isNotEmpty() }?.toTypedArray(),
                null,
                null,
                "${CompanyTable.COMPANY_NAME} COLLATE NOCASE ASC"
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.toCompany()) }
            }
        }

    override suspend fun getById(id: Long): Company? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            CompanyTable.NAME, null, "${CompanyTable.ID} = ?", arrayOf(id.toString()),
            null, null, null
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toCompany() else null }
    }

    override suspend fun insert(company: Company): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertOrThrow(CompanyTable.NAME, null, company.toValues())
    }

    override suspend fun update(company: Company): Boolean = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            CompanyTable.NAME, company.toValues(), "${CompanyTable.ID} = ?", arrayOf(company.id.toString())
        ) > 0
    }

    override suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            CompanyTable.NAME, "${CompanyTable.ID} = ?", arrayOf(id.toString())
        ) > 0
    }
}

// ---------- Mapeo entre filas de la base y el modelo ----------

private fun Company.toValues() = ContentValues().apply {
    put(CompanyTable.COMPANY_NAME, name)
    put(CompanyTable.WEBSITE, website)
    put(CompanyTable.PHONE, phone)
    put(CompanyTable.EMAIL, email)
    put(CompanyTable.PRODUCTS, productsAndServices)
    put(CompanyTable.CLASSIFICATION, Classification.toMask(classifications))
}

private fun Cursor.toCompany() = Company(
    id = getLong(getColumnIndexOrThrow(CompanyTable.ID)),
    name = text(CompanyTable.COMPANY_NAME),
    website = text(CompanyTable.WEBSITE),
    phone = text(CompanyTable.PHONE),
    email = text(CompanyTable.EMAIL),
    productsAndServices = text(CompanyTable.PRODUCTS),
    classifications = Classification.fromMask(getInt(getColumnIndexOrThrow(CompanyTable.CLASSIFICATION)))
)

private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()

/** Escapa % y _ para que el texto buscado se trate literalmente en LIKE. */
private fun escapeLike(text: String): String =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")