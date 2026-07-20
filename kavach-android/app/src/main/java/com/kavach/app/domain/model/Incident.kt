package com.kavach.app.domain.model

/**
 * Domain model for an elder-safety incident.
 * Maps from both [IncidentEntity] (local) and future API responses.
 */
data class Incident(
    val id: String,
    val elderId: String,
    val status: IncidentStatus,
    val riskScore: Double,
    val startedAt: String,
    val resolvedAt: String? = null,
    val resolutionNote: String? = null,
)

enum class IncidentStatus(val value: String) {
    OPEN("open"),
    GRADUATED_1("graduated_1"),
    GRADUATED_2("graduated_2"),
    GRADUATED_3("graduated_3"),
    GRADUATED_4("graduated_4"),
    RESOLVED("resolved"),
    FALSE_POSITIVE("false_positive");

    val isActive: Boolean get() = this in listOf(OPEN, GRADUATED_1, GRADUATED_2, GRADUATED_3, GRADUATED_4)

    companion object {
        fun fromString(value: String): IncidentStatus =
            entries.firstOrNull { it.value == value } ?: OPEN
    }
}

data class ResolvedIncident(
    val incidentId: String,
    val status: String,
    val resolvedAt: String,
)

data class EvidencePackage(
    val incidentId: String,
    val pdfRef: String,
    val downloadUrl: String?,
)
