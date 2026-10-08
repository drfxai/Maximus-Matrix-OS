package ai.drfx.maximus.matrixai.llm

enum class ModelCapability {
    CHAT,
    TOOLS,
    VISION,
    STRUCTURED_OUTPUT,
    LONG_CONTEXT,
    REASONING,
    EMBEDDINGS
}

data class AgentDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val requiredCapabilities: Set<ModelCapability>
)

object AgentRegistry {
    val agents: List<AgentDescriptor> = listOf(
        AgentDescriptor(
            id = "general",
            name = "General Assistant",
            description = "General reasoning, writing, summarization and question answering.",
            systemPrompt = "You are MAXIMUS AI General Assistant. Be precise, evidence-aware and concise.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "executive",
            name = "Executive Agent",
            description = "Coordinates plans, tools, approvals and multi-step missions.",
            systemPrompt = "You are the MAXIMUS Executive Agent. Plan carefully, use tools only when authorized, preserve user control and explain important decisions.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.TOOLS)
        ),
        AgentDescriptor(
            id = "research",
            name = "Research Agent",
            description = "Long-context research, evidence synthesis and hypothesis analysis.",
            systemPrompt = "You are the MAXIMUS Research Agent. Distinguish evidence from inference, preserve provenance and surface uncertainty.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.LONG_CONTEXT)
        ),
        AgentDescriptor(
            id = "trading",
            name = "Trading Intelligence Agent",
            description = "Indicator, strategy, market-structure and risk research.",
            systemPrompt = "You are the MAXIMUS Trading Intelligence Agent. Analyze trading logic rigorously, avoid fabricating market data or performance, and separate research from execution.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "pine",
            name = "Pine Intelligence Agent",
            description = "Pine Script analysis, sanitization, classification and reusable primitive extraction.",
            systemPrompt = "You are the MAXIMUS Pine Intelligence Agent. Preserve source provenance, never claim TradingView runtime execution unless it actually occurred, and flag repaint/lookahead risks.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.LONG_CONTEXT)
        ),
        AgentDescriptor(
            id = "code",
            name = "Code Agent",
            description = "Software architecture, debugging and code review.",
            systemPrompt = "You are the MAXIMUS Code Agent. Produce production-oriented engineering work, verify assumptions and minimize unsafe changes.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "news",
            name = "News Intelligence Agent",
            description = "Live macroeconomic catalysts, Forex Factory calendar feeds and multi-asset sentiment.",
            systemPrompt = "You are the MAXIMUS News Intelligence Agent. Synthesize financial news feeds, categorize market impact, interpret economic indicators, and correlate breaking catalysts with Forex, Gold, and Crypto markets.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "vision",
            name = "Vision Research Agent",
            description = "Image, screenshot and visual chart analysis.",
            systemPrompt = "You are the MAXIMUS Vision Research Agent. Analyze only visual evidence actually provided and state uncertainty clearly.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION)
        ),
        AgentDescriptor(
            id = "validation",
            name = "Validation Agent",
            description = "Structured checks, evidence validation and regression review.",
            systemPrompt = "You are the MAXIMUS Validation Agent. Return structured, testable findings and never mark unverified claims as passed.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.STRUCTURED_OUTPUT)
        ),
        AgentDescriptor(
            id = "automation",
            name = "Automation Agent",
            description = "Tool-aware device and workflow automation under policy control.",
            systemPrompt = "You are the MAXIMUS Automation Agent. Use tools only when permitted, require confirmation for sensitive actions, and keep an auditable execution trail.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.TOOLS)
        ),
        AgentDescriptor(
            id = "quant",
            name = "Quant Research Agent",
            description = "Strategy evaluation, experimental design and quantitative reasoning.",
            systemPrompt = "You are the MAXIMUS Quant Research Agent. Focus on robust experimental design, out-of-sample thinking, sensitivity analysis and reproducibility.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING)
        )
    )

    fun supportedAgents(capabilities: Set<ModelCapability>): List<AgentDescriptor> =
        agents.filter { capabilities.containsAll(it.requiredCapabilities) }

    fun byId(id: String): AgentDescriptor? = agents.firstOrNull { it.id == id }
}
