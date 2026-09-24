package com.markel.flowstate.core.domain

data class Category(
    val id: Int = 0,
    val name: String,
    val position: Int = 0
) {
    companion object {
        /**
         * The fixed id of the "General" category — the default category that
         * every task, idea and checklist belongs to when no user category is
         * assigned (or when categories are disabled).
         *
         * This row is inserted by MIGRATION_18_19
         * and can never be deleted by the user.
         */
        const val GENERAL_ID = 1

        /**
         * Returns whether [name] is a shipped or currently localized display
         * name of the built-in General category.
         *
         * Callers that render localized UI should pass [localizedGeneralName].
         * Persistence boundaries can omit it and still protect every name
         * shipped by this version of the app.
         */
        fun isReservedGeneralName(
            name: String,
            localizedGeneralName: String? = null,
        ): Boolean {
            val trimmedName = name.trim()
            val matchesLocalizedName = !localizedGeneralName.isNullOrBlank() &&
                trimmedName.equals(localizedGeneralName.trim(), ignoreCase = true)
            return matchesLocalizedName || SHIPPED_GENERAL_NAMES.any {
                trimmedName.equals(it, ignoreCase = true)
            }
        }

        private val SHIPPED_GENERAL_NAMES = setOf("General", "常规")
    }
}
