package ai.drfx.maximus.matrixai.agent

class MatrixPolicyEngine {
    private val deniedTools = setOf("disable_audit", "bypass_policy", "export_secrets")
    private val confirmationTools = setOf(
        "web_search", "open_url", "open_settings", "open_camera", "set_alarm",
        "create_calendar_event", "open_dialer", "compose_sms", "share_text",
        "copy_clipboard", "open_app", "create_note",
        "make_phone_call",
        "send_sms",
        "delete_memory",
        "delete_artifact",
        "publish_artifact",
        "modify_permissions",
        "execute_external_action"
    )

    fun evaluate(action: AgentAction): PolicyDecision = when {
        action.tool in deniedTools -> PolicyDecision.DENY
        action.risk == RiskLevel.CRITICAL -> PolicyDecision.DENY
        action.risk == RiskLevel.HIGH || action.tool in confirmationTools -> PolicyDecision.REQUIRE_CONFIRMATION
        action.tool in setOf("system_status", "device_info") -> PolicyDecision.ALLOW
        else -> PolicyDecision.DENY
    }
}
