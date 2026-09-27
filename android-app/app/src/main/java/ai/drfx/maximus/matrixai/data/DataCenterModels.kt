package ai.drfx.maximus.matrixai.data

data class DataCenterStatus(
    val connected: Boolean = false,
    val name: String = "Company Data Center",
    val pineSources: Int? = null,
    val documents: Int? = null,
    val projects: Int? = null,
    val primitives: Int? = null,
    val message: String = "No company data source is connected."
)

data class KnowledgeItem(
    val id: String,
    val title: String,
    val type: String,
    val summary: String
)

data class DataCenterUiState(
    val baseUrl: String = "",
    val status: DataCenterStatus = DataCenterStatus(),
    val searchResults: List<KnowledgeItem> = emptyList(),
    val busy: Boolean = false,
    val query: String = ""
)
