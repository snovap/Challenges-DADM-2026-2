package co.edu.unal.directorio.model

/**
 * Clasificación de una empresa. Cada valor ocupa un bit, así una empresa puede tener
 * varias a la vez y se guardan en una sola columna INTEGER (máscara de bits).
 */
enum class Classification(val bit: Int, val label: String) {
    CONSULTING(1, "Consultoría"),
    CUSTOM_DEVELOPMENT(2, "Desarrollo a la medida"),
    SOFTWARE_FACTORY(4, "Fábrica de software");

    companion object {
        fun fromMask(mask: Int): Set<Classification> =
            entries.filter { (mask and it.bit) != 0 }.toSet()

        fun toMask(items: Set<Classification>): Int =
            items.fold(0) { acc, item -> acc or item.bit }
    }
}

data class Company(
    val id: Long = 0L,
    val name: String,
    val website: String,
    val phone: String,
    val email: String,
    val productsAndServices: String,
    val classifications: Set<Classification>
)