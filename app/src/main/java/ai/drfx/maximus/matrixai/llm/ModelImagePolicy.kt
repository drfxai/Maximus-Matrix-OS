package ai.drfx.maximus.matrixai.llm

import org.json.JSONObject

/**
 * Catalog presence proves that a model ID exists, not which modalities it accepts.
 * Never advertise unknown 9Router image support as verified; allow an explicit user
 * image request to reach that authenticated route so the provider can decide.
 */
object ModelImagePolicy {
    fun fromCatalog(model: JSONObject): ImageInputSupport {
        val capabilities = model.optJSONObject("capabilities")
        val declaredInputs = model.optJSONArray("input_modalities")
            ?: model.optJSONObject("architecture")?.optJSONArray("input_modalities")
            ?: capabilities?.optJSONArray("input_modalities")
        if (declaredInputs != null) {
            val acceptsImage = (0 until declaredInputs.length()).any {
                declaredInputs.optString(it).equals("image", ignoreCase = true)
            }
            return if (acceptsImage) ImageInputSupport.SUPPORTED else ImageInputSupport.UNSUPPORTED
        }
        return when (capabilities?.opt("vision")) {
            true -> ImageInputSupport.SUPPORTED
            false -> ImageInputSupport.UNSUPPORTED
            else -> ImageInputSupport.UNKNOWN
        }
    }

    fun canAttemptImage(provider: LlmProvider, model: ModelDescriptor?): Boolean {
        if (model?.verified != true) return false
        return when (model.imageInputSupport) {
            ImageInputSupport.SUPPORTED -> true
            ImageInputSupport.UNSUPPORTED -> false
            ImageInputSupport.UNKNOWN ->
                ModelCapability.VISION in model.capabilities || provider.isNineRouter
        }
    }

    fun requiresUnverifiedRouterAttempt(provider: LlmProvider, model: ModelDescriptor?): Boolean =
        provider.isNineRouter && model?.verified == true &&
            model.imageInputSupport == ImageInputSupport.UNKNOWN &&
            ModelCapability.VISION !in model.capabilities
}
