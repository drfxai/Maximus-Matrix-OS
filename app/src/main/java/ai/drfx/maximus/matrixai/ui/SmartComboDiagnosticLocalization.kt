package ai.drfx.maximus.matrixai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ai.drfx.maximus.matrixai.R

/**
 * Render only known Smart Combo diagnostics using user-selected Android resources.
 * Provider names and model identifiers remain unchanged; no generic translation of
 * server responses, credentials, or model output is performed.
 */
internal object SmartComboDiagnosticPolicy {
    enum class Kind {
        VERIFYING, CHECKING, DISABLED, CATALOG_UNAVAILABLE, NO_ACCOUNT,
        NO_FREE_ROUTE, VERIFIED_ROUTES, PROVIDER_UNAVAILABLE, NO_CHAT,
        NO_FREE_PRICE, NO_CONTEXT, ELIGIBLE_ROUTES, NO_ELIGIBLE,
        UNRECOGNIZED
    }

    data class Diagnosis(
        val kind: Kind,
        val count: Int = 0,
        val provider: String = "",
        val original: String = ""
    )

    private val verified = Regex("""^(\d+) verified zero-price catalog routes\. Remaining account quota is unknown\.$""")
    private val providerReady = Regex("""^(\d+) verified free text route\(s\); live quota unknown\.$""")
    private val missingContext = Regex("""^(\d+) free-price model\(s\), but context length is not verified\.$""")

    fun parse(raw: String): Diagnosis {
        val colon = raw.indexOf(": ")
        val provider = if (colon > 0) raw.substring(0, colon).take(80) else ""
        val content = if (provider.isNotEmpty()) raw.substring(colon + 2) else raw
        val count = verified.matchEntire(content)?.groupValues?.get(1)?.toIntOrNull()
        if (count != null) return Diagnosis(Kind.VERIFIED_ROUTES, count, provider, raw)
        val ready = providerReady.matchEntire(content)?.groupValues?.get(1)?.toIntOrNull()
        if (ready != null) return Diagnosis(Kind.ELIGIBLE_ROUTES, ready, provider, raw)
        val withoutContext = missingContext.matchEntire(content)?.groupValues?.get(1)?.toIntOrNull()
        if (withoutContext != null) return Diagnosis(Kind.NO_CONTEXT, withoutContext, provider, raw)
        val kind = when (content) {
            "Verifying free model offers…" -> Kind.VERIFYING
            "Checking free model catalogues…" -> Kind.CHECKING
            "Free-only routing is off." -> Kind.DISABLED
            "Free catalogue unavailable. No automatic paid fallback." -> Kind.CATALOG_UNAVAILABLE
            "No configured account found. Add a provider key, or switch off free-only routing." -> Kind.NO_ACCOUNT
            "No catalog-confirmed zero-price text route. An API key or promotional credits do not prove a model is free." -> Kind.NO_FREE_ROUTE
            "Catalog unavailable or authentication failed; no free-pricing claim." -> Kind.PROVIDER_UNAVAILABLE
            "Catalog contains no verified chat-capable model." -> Kind.NO_CHAT
            "No published zero-price text pricing; free tier not proven." -> Kind.NO_FREE_PRICE
            "No eligible free model." -> Kind.NO_ELIGIBLE
            else -> Kind.UNRECOGNIZED
        }
        return Diagnosis(kind, provider = provider, original = raw)
    }
}

@Composable
internal fun localizedSmartComboDiagnostic(raw: String): String {
    val info = SmartComboDiagnosticPolicy.parse(raw)
    val text = when (info.kind) {
        SmartComboDiagnosticPolicy.Kind.VERIFYING -> stringResource(R.string.combo_diag_verifying)
        SmartComboDiagnosticPolicy.Kind.CHECKING -> stringResource(R.string.combo_diag_checking)
        SmartComboDiagnosticPolicy.Kind.DISABLED -> stringResource(R.string.combo_diag_disabled)
        SmartComboDiagnosticPolicy.Kind.CATALOG_UNAVAILABLE -> stringResource(R.string.combo_diag_unavailable)
        SmartComboDiagnosticPolicy.Kind.NO_ACCOUNT -> stringResource(R.string.combo_diag_no_account)
        SmartComboDiagnosticPolicy.Kind.NO_FREE_ROUTE -> stringResource(R.string.combo_diag_no_free)
        SmartComboDiagnosticPolicy.Kind.VERIFIED_ROUTES -> stringResource(R.string.combo_diag_verified, info.count)
        SmartComboDiagnosticPolicy.Kind.PROVIDER_UNAVAILABLE -> stringResource(R.string.combo_diag_provider_unavailable)
        SmartComboDiagnosticPolicy.Kind.NO_CHAT -> stringResource(R.string.combo_diag_no_chat)
        SmartComboDiagnosticPolicy.Kind.NO_FREE_PRICE -> stringResource(R.string.combo_diag_no_price)
        SmartComboDiagnosticPolicy.Kind.NO_CONTEXT -> stringResource(R.string.combo_diag_no_context, info.count)
        SmartComboDiagnosticPolicy.Kind.ELIGIBLE_ROUTES -> stringResource(R.string.combo_diag_provider_ready, info.count)
        SmartComboDiagnosticPolicy.Kind.NO_ELIGIBLE -> stringResource(R.string.combo_diag_no_eligible)
        SmartComboDiagnosticPolicy.Kind.UNRECOGNIZED -> return raw
    }
    // Display verified provider brand only, never an endpoint or credential.
    return if (info.provider.isBlank()) text else info.provider + ": " + text
}
