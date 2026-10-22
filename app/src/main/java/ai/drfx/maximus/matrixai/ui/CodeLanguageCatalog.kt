package ai.drfx.maximus.matrixai.ui

import ai.drfx.maximus.matrixai.studio.PineSourceDetector

/** Conservative source-code language classification. Never interprets or executes source text. */
internal data class CodeLanguage(val label: String, val extension: String, val canPreviewHtml: Boolean = false)

internal object CodeLanguageCatalog {
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
