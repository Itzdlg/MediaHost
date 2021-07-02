package me.schooltests.mediahost.data.content

enum class PrivacyType(val displayName: String, val id: Int) {
    PUBLIC("public", 0), PRIVATE("private", 1);

    companion object {
        fun from(id: Int): PrivacyType {
            return when (id) {
                0 -> PUBLIC
                1 -> PRIVATE
                else -> PUBLIC
            }
        }

        fun from(name: String): PrivacyType {
            return when (name) {
                "public" -> PUBLIC
                "private" -> PRIVATE
                else -> PUBLIC
            }
        }
    }
}