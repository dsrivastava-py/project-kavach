package com.kavach.app.domain.model

/**
 * Domain model returned after a successful guardian pairing.
 * Completely decoupled from [PairGuardianResponse] DTO.
 */
data class GuardianSession(
    val guardianId: String,
    val token: String,
)

/**
 * Domain model returned after an elder generates a pairing code.
 */
data class PairingCode(
    val code: String,
    val expiresInSeconds: Int,
)

/** All roles the backend recognises. */
enum class UserRole(val value: String) {
    ELDER("elder"),
    GUARDIAN("guardian"),
    ADULT_CHILD("adult_child"),
    INVESTIGATOR("investigator");

    companion object {
        fun fromString(value: String): UserRole =
            entries.firstOrNull { it.value == value } ?: GUARDIAN
    }
}
