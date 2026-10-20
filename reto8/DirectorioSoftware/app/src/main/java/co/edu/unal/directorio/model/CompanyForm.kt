package co.edu.unal.directorio.model

data class FormErrors(
    val name: String? = null,
    val website: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val classification: String? = null
) {
    val hasErrors: Boolean
        get() = listOfNotNull(name, website, phone, email, classification).isNotEmpty()
}

/** Datos crudos del formulario. Sabe validarse y convertirse en [Company]. */
data class CompanyForm(
    val name: String = "",
    val website: String = "",
    val phone: String = "",
    val email: String = "",
    val productsAndServices: String = "",
    val classifications: Set<Classification> = emptySet()
) {
    /** Nombre y clasificación son obligatorios; los demás campos se validan solo si se llenan. */
    fun validate(): FormErrors = FormErrors(
        name = if (name.isBlank()) "El nombre es obligatorio" else null,
        website = invalidIf(website, URL_REGEX, "URL no válida"),
        phone = invalidIf(phone, PHONE_REGEX, "Teléfono no válido"),
        email = invalidIf(email, EMAIL_REGEX, "Email no válido"),
        classification = if (classifications.isEmpty()) "Selecciona al menos una clasificación" else null
    )

    fun toCompany(id: Long = 0L): Company = Company(
        id = id,
        name = name.trim(),
        website = normalizedWebsite(),
        phone = phone.trim(),
        email = email.trim(),
        productsAndServices = productsAndServices.trim(),
        classifications = classifications
    )

    /** Agrega https:// si falta, para que el navegador pueda abrir la URL. */
    private fun normalizedWebsite(): String {
        val url = website.trim()
        return if (url.isEmpty() || url.startsWith("http://") || url.startsWith("https://")) url
        else "https://$url"
    }

    private fun invalidIf(value: String, regex: Regex, message: String): String? =
        value.trim().takeIf { it.isNotEmpty() && !regex.matches(it) }?.let { message }

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val URL_REGEX = Regex("^(https?://)?[\\w-]+(\\.[\\w-]+)+(/\\S*)?$")
        private val PHONE_REGEX = Regex("^\\+?[\\d\\s()-]{7,20}$")

        fun from(company: Company) = CompanyForm(
            name = company.name,
            website = company.website,
            phone = company.phone,
            email = company.email,
            productsAndServices = company.productsAndServices,
            classifications = company.classifications
        )
    }
}