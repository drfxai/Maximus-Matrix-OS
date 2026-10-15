package ai.drfx.maximus.matrixai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.llm.FreeCatalogCheckKind
import ai.drfx.maximus.matrixai.llm.FreeCatalogCheckUi
import ai.drfx.maximus.matrixai.llm.FreeCatalogUiPhase
import ai.drfx.maximus.matrixai.llm.LlmUiState

/**
 * UI-only translations of typed provider evidence.
 *
 * Never infer a confirmed free quota from an API key, a model name,
 * an estimated context window, or a verified catalog zero-price offer.
 */
@Composable
internal fun smartComboStatusLabel(state: LlmUiState): String =
    when (state.smartComboUiPhase) {
        FreeCatalogUiPhase.OFF -> stringResource(
            if (state.smartComboEnabled) R.string.combo_state_checking
            else R.string.combo_state_off
        )
        FreeCatalogUiPhase.CHECKING -> stringResource(R.string.combo_state_checking)
        FreeCatalogUiPhase.NO_ACCOUNTS -> stringResource(R.string.combo_state_no_accounts)
        FreeCatalogUiPhase.NONE_VERIFIED -> stringResource(R.string.combo_state_no_free)
        FreeCatalogUiPhase.READY ->
            stringResource(R.string.combo_state_ready, state.smartComboOffers)
        FreeCatalogUiPhase.UNAVAILABLE -> stringResource(R.string.combo_state_unavailable)
    }

@Composable
internal fun smartComboProviderLabel(check: FreeCatalogCheckUi): String {
    val detail = when (check.kind) {
        FreeCatalogCheckKind.VERIFIED_FREE ->
            stringResource(R.string.combo_check_free, check.count)
        FreeCatalogCheckKind.CATALOG_UNAVAILABLE ->
            stringResource(R.string.combo_check_unavailable)
        FreeCatalogCheckKind.NO_CHAT_MODEL ->
            stringResource(R.string.combo_check_no_chat)
        FreeCatalogCheckKind.NO_ZERO_PRICE ->
            stringResource(R.string.combo_check_no_zero_price)
        FreeCatalogCheckKind.MISSING_CONTEXT ->
            stringResource(R.string.combo_check_missing_context, check.count)
        FreeCatalogCheckKind.NO_ELIGIBLE_MODEL ->
            stringResource(R.string.combo_check_ineligible)
    }
    return check.provider.displayName + ": " + detail
}
