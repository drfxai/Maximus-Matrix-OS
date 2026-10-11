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
    val requiredCapabilities: Set<ModelCapability>,
    val executionKind: AgentExecutionKind = AgentExecutionKind.CONVERSATIONAL_PERSONA
)

enum class AgentExecutionKind { CONVERSATIONAL_PERSONA }

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
            name = "Executive Persona",
            description = "Planning conversation only; does not execute tools.",
            systemPrompt = "You are the MAXIMUS Executive planning persona. Provide plans and explain decisions. You cannot execute tools or device actions; never claim that you did.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.TOOLS)
        ),
        AgentDescriptor(
            id = "research",
            name = "Research Persona",
            description = "Long-context research, evidence synthesis and hypothesis analysis.",
            systemPrompt = "You are the MAXIMUS Research Persona. Distinguish evidence from inference, preserve provenance and surface uncertainty.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.LONG_CONTEXT)
        ),
        AgentDescriptor(
            id = "trading",
            name = "Trading Intelligence Persona",
            description = "Indicator, strategy, market-structure and risk research.",
            systemPrompt = "You are the MAXIMUS Trading Intelligence Persona. Analyze trading logic rigorously, avoid fabricating market data or performance, and separate research from execution.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "pine",
            name = "Pine Intelligence Persona",
            description = "Pine Script analysis, sanitization, classification and reusable primitive extraction.",
            systemPrompt = "You are the MAXIMUS Pine Intelligence Persona. Preserve source provenance, never claim TradingView runtime execution unless it actually occurred, and flag repaint/lookahead risks.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.LONG_CONTEXT)
        ),
        AgentDescriptor(
            id = "code",
            name = "Code Persona",
            description = "Software architecture, debugging and code review.",
            systemPrompt = "You are the MAXIMUS Code Persona. Produce production-oriented engineering work, verify assumptions and minimize unsafe changes.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "news",
            name = "News Research Persona",
            description = "Discusses news supplied in the conversation; no live feed access.",
            systemPrompt = "Discuss only news and sources actually supplied in the conversation. Do not claim access to live news or economic-calendar feeds.",
            requiredCapabilities = setOf(ModelCapability.CHAT)
        ),
        AgentDescriptor(
            id = "vision",
            name = "Vision Research Persona",
            description = "Image, screenshot and visual chart analysis.",
            systemPrompt = "You are the MAXIMUS Vision Research Persona. Analyze only visual evidence actually provided and state uncertainty clearly.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.VISION)
        ),
        AgentDescriptor(
            id = "validation",
            name = "Validation Persona",
            description = "Structured checks, evidence validation and regression review.",
            systemPrompt = "You are the MAXIMUS Validation Persona. Return structured, testable findings and never mark unverified claims as passed.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.STRUCTURED_OUTPUT)
        ),
        AgentDescriptor(
            id = "automation",
            name = "Automation Planning Persona",
            description = "Discusses workflow plans; does not execute device actions or tools.",
            systemPrompt = "You are the MAXIMUS Automation planning persona. Suggest workflows but do not claim to execute tools or device actions.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.TOOLS)
        ),
        AgentDescriptor(
            id = "quant",
            name = "Quant Research Persona",
            description = "Strategy evaluation, experimental design and quantitative reasoning.",
            systemPrompt = "You are the MAXIMUS Quant Research Persona. Focus on robust experimental design, out-of-sample thinking, sensitivity analysis and reproducibility.",
            requiredCapabilities = setOf(ModelCapability.CHAT, ModelCapability.REASONING)
        )
    )

    fun supportedAgents(capabilities: Set<ModelCapability>): List<AgentDescriptor> =
        agents.filter { capabilities.containsAll(it.requiredCapabilities) }

    fun byId(id: String): AgentDescriptor? = agents.firstOrNull { it.id == id }
}
