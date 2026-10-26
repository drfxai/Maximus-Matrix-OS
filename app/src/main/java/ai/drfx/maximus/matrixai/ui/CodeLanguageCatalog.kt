package ai.drfx.maximus.matrixai.ui

import ai.drfx.maximus.matrixai.studio.PineSourceDetector

/** Conservative source-code language classification. Never interprets or executes source text. */
internal data class CodeLanguage(val label: String, val extension: String, val canPreviewHtml: Boolean = false)

internal object CodeLanguageCatalog {
    /** Fence tags that say nothing about the language, so the content decides. */
    private val genericTags = setOf("", "text", "txt", "plain", "plaintext", "code", "source", "snippet")

    /** Language of an attached file from its extension, e.g. "strategy.py" → Python. */
    fun forFileName(name: String): CodeLanguage? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return null
        return resolve(ext, "").takeIf { it.extension != "txt" }
    }

    /**
     * Content-based guess for unlabelled code. Scores distinctive constructs per language and
     * only answers when one language clearly leads, so prose is never mislabelled as code.
     */
    fun detect(source: String): CodeLanguage? {
        val text = source.take(6_000)
        if (text.isBlank()) return null
        if (PineSourceDetector.looksLikePine(text)) return CodeLanguage("Pine Script", "pine")
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<!DOCTYPE html", true) || trimmed.startsWith("<html", true)) return CodeLanguage("HTML", "html", true)
        if (trimmed.startsWith("<?xml")) return CodeLanguage("XML", "html")
        if (trimmed.startsWith("<?php")) return CodeLanguage("PHP", "php")
        if (trimmed.startsWith("#!") && (trimmed.lineSequence().first().contains("sh"))) return CodeLanguage("Shell", "sh")
        if (trimmed.startsWith("#!") && trimmed.lineSequence().first().contains("python")) return CodeLanguage("Python", "py")
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && runCatching { looksLikeJson(trimmed) }.getOrDefault(false))
            return CodeLanguage("JSON", "json")
        fun count(pattern: String) = Regex(pattern, RegexOption.MULTILINE).findAll(text).count().coerceAtMost(6)
        val scores = mapOf(
            CodeLanguage("Python", "py") to count("^\\s*def \\w+\\(.*\\)\\s*(->.*)?:\\s*$") * 3 + count("^\\s*(from \\w[\\w.]* )?import \\w") +
                count("^\\s*(if|elif|for|while|with|try|except|class)\\b.*:\\s*$") * 2 + count("\\bself\\.") + count("\\bprint\\(") +
                count("\\b(None|True|False)\\b"),
            CodeLanguage("Java", "java") to count("\\bpublic\\s+(static\\s+)?(class|void|final)\\b") * 2 + count("System\\.out\\.print") * 3 +
                count("^import java\\.") * 3 + count("\\bString\\[\\]") * 2 + count("@Override"),
            CodeLanguage("Kotlin", "kt") to count("^\\s*(private |internal |override )?fun \\w+") * 3 + count("^\\s*val \\w+") + count("^\\s*var \\w+") +
                count("\\bprintln\\(") + count("^package [\\w.]+$") + count("\\bdata class\\b") * 2,
            CodeLanguage("C++", "cpp") to count("#include\\s*<(iostream|vector|string|map|memory)>") * 3 + count("\\bstd::") * 2 +
                count("\\bcout\\s*<<") * 2 + count("\\btemplate\\s*<") * 2 + count("\\bnamespace\\b"),
            CodeLanguage("C", "c") to count("#include\\s*<(stdio|stdlib|string|math)\\.h>") * 3 + count("\\bprintf\\(") +
                count("\\bint\\s+main\\s*\\(") * 2 + count("\\bmalloc\\(") + count("\\bstruct\\s+\\w+\\s*\\{"),
            CodeLanguage("C#", "cs") to count("^using System") * 3 + count("\\bnamespace\\s+\\w") + count("Console\\.Write") * 3 +
                count("\\bpublic\\s+(async\\s+)?Task\\b") * 2,
            CodeLanguage("JavaScript", "js") to count("\\b(const|let)\\s+\\w+\\s*=") + count("=>") + count("\\bfunction\\s*\\w*\\(") * 2 +
                count("console\\.log\\(") * 2 + count("\\brequire\\(") + count("document\\.") + count("^export (default )?") ,
            CodeLanguage("TypeScript", "ts") to count("\\binterface\\s+\\w+\\s*\\{") * 2 + count(":\\s*(string|number|boolean)\\b") * 2 +
                count("^import .* from ['\"]") + count("\\btype\\s+\\w+\\s*="),
            CodeLanguage("Rust", "rs") to count("\\bfn\\s+\\w+") * 2 + count("\\blet\\s+mut\\b") * 3 + count("println!\\(") * 3 + count("\\bimpl\\b") * 2 +
                count("::"),
            CodeLanguage("Go", "go") to count("^package \\w+$") * 2 + count("\\bfunc\\s+(\\(\\w+ \\*?\\w+\\)\\s*)?\\w+\\(") * 3 + count(":=") * 2 +
                count("fmt\\.Print"),
            CodeLanguage("SQL", "sql") to count("(?i)^\\s*(select|insert into|update|delete from|create table|alter table)\\b") * 3 +
                count("(?i)\\b(from|where|join|group by|order by)\\b"),
            CodeLanguage("Shell", "sh") to count("^\\s*(echo|cd|export|sudo|apt|npm|pip|git|curl|chmod|mkdir)\\b") * 2 +
                count("\\$\\{?\\w+") + count("^\\s*(fi|done|esac)$") * 3,
            CodeLanguage("CSS", "css") to count("^\\s*[.#]?[\\w-]+(\\s*[,>+~]?\\s*[.#]?[\\w-:]+)*\\s*\\{\\s*$") * 2 +
                count("^\\s*[\\w-]+\\s*:\\s*[^;]+;\\s*$"),
            CodeLanguage("MQL5", "mq5") to count("\\b(OnTick|OnInit|OnCalculate|OnDeinit)\\s*\\(") * 3 + count("#property\\b") * 2,
            CodeLanguage("Ruby", "rb") to count("^\\s*def \\w+[^:]*$") + count("^\\s*end$") * 2 + count("\\bputs\\b") * 2 + count("\\.each do\\b") * 2,
            CodeLanguage("YAML", "yaml") to count("^[\\w-]+:\\s*$") + count("^\\s+- \\w") + count("^\\s*[\\w-]+:\\s+\\S")
        )
        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.value ?: 0
        return if (best.value >= 3 && best.value > runnerUp) best.key else null
    }

    private fun looksLikeJson(text: String): Boolean {
        val sample = text.trim()
        return (sample.startsWith("{") && sample.endsWith("}") || sample.startsWith("[") && sample.endsWith("]")) &&
            Regex("\"[^\"]+\"\\s*:").containsMatchIn(sample.take(2000))
    }

    fun resolve(rawTag: String?, source: String): CodeLanguage {
        val tag = rawTag.orEmpty().trim().lowercase().substringBefore(' ')
            .removePrefix(".").take(32)
        // Models often fence Pine as js/javascript/c/text; the content decides.
        if (tag !in setOf("html", "htm", "xml", "svg", "css", "json") && PineSourceDetector.looksLikePine(source))
            return CodeLanguage("Pine Script", "pine")
        return when(tag) {
            "html", "htm", "xml", "svg" -> CodeLanguage("HTML", "html", true)
            "css", "scss", "sass" -> CodeLanguage("CSS", "css")
            "javascript", "js", "jsx", "mjs" -> CodeLanguage("JavaScript", "js")
            "typescript", "ts", "tsx" -> CodeLanguage("TypeScript", "ts")
            "python", "py" -> CodeLanguage("Python", "py")
            "kotlin", "kt", "kts" -> CodeLanguage("Kotlin", "kt")
            "java" -> CodeLanguage("Java", "java")
            "pine", "pinescript", "pine-script", "pine_script", "pinev5", "pinev6", "tradingview" -> CodeLanguage("Pine Script", "pine")
            "rust", "rs" -> CodeLanguage("Rust", "rs")
            "go", "golang" -> CodeLanguage("Go", "go")
            "cpp", "c++", "cc", "cxx", "hpp" -> CodeLanguage("C++", "cpp")
            "c" -> CodeLanguage("C", "c")
            "csharp", "cs", "c#" -> CodeLanguage("C#", "cs")
            "swift" -> CodeLanguage("Swift", "swift")
            "dart" -> CodeLanguage("Dart", "dart")
            "ruby", "rb" -> CodeLanguage("Ruby", "rb")
            "php" -> CodeLanguage("PHP", "php")
            "bash", "shell", "sh", "zsh" -> CodeLanguage("Shell", "sh")
            "powershell", "ps1" -> CodeLanguage("PowerShell", "ps1")
            "sql", "mysql", "postgresql" -> CodeLanguage("SQL", "sql")
            "json", "jsonc" -> CodeLanguage("JSON", "json")
            "yaml", "yml" -> CodeLanguage("YAML", "yaml")
            "toml" -> CodeLanguage("TOML", "toml")
            "markdown", "md" -> CodeLanguage("Markdown", "md")
            "mql4", "mq4" -> CodeLanguage("MQL4", "mq4")
            "mql5", "mq5" -> CodeLanguage("MQL5", "mq5")
            "lua" -> CodeLanguage("Lua", "lua")
            "h" -> CodeLanguage("C", "c")
            in genericTags -> detect(source) ?: CodeLanguage(if (tag.isBlank()) "Code" else rawTag.orEmpty().take(32), "txt")
            else -> {
                val text = source.trimStart().take(350)
                when {
                    text.startsWith("<!DOCTYPE html", true) || text.startsWith("<html", true) ->
                        CodeLanguage("HTML", "html", true)
                    text.startsWith("//@version=") || Regex("""\b(indicator|strategy)\s*\(""")
                        .containsMatchIn(text) -> CodeLanguage("Pine Script", "pine")
                    text.startsWith("<?php", true) -> CodeLanguage("PHP", "php")
                    text.startsWith("#!/bin/bash") || text.startsWith("#!/usr/bin/env bash") ->
                        CodeLanguage("Shell", "sh")
                    text.startsWith("package main") && text.contains("func ") ->
                        CodeLanguage("Go", "go")
                    else -> CodeLanguage(if (tag.isBlank()) "Code" else rawTag.orEmpty().take(32), "txt")
                }
            }
        }
    }
}
