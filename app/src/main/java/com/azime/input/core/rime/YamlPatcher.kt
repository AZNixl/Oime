package com.azime.input.core.rime

import java.io.File

object YamlPatcher {
    fun patchSchemaList(file: File, schemaIds: List<String>): Boolean {
        val existingContent = if (file.exists()) file.readText() else ""
        val newContent = buildPatchedYaml(existingContent, schemaIds)
        if (existingContent == newContent) return false
        file.writeText(newContent)
        return true
    }

    private fun buildPatchedYaml(original: String, schemaIds: List<String>): String {
        val lines = original.lines()
        val result = mutableListOf<String>()
        var inPatch = false
        var inSchemaList = false
        var schemaListIndent = 2
        var schemaListInserted = false

        for (line in lines) {
            val cleanLine = line.removeSuffix("\r")
            val trimmed = cleanLine.trimStart()
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                result.add(cleanLine)
                continue
            }
            if (cleanLine.startsWith("patch:")) {
                inPatch = true
                result.add(cleanLine)
                continue
            }
            if (inPatch && trimmed.startsWith("schema_list:")) {
                inSchemaList = true
                schemaListIndent = cleanLine.indexOf("schema_list:")
                continue
            }
            if (inSchemaList) {
                val indent = cleanLine.takeWhile { it.isWhitespace() }.length
                if (trimmed.isNotEmpty() && indent <= schemaListIndent) {
                    if (!schemaListInserted) {
                        insertSchemaList(result, schemaListIndent, schemaIds)
                        schemaListInserted = true
                    }
                    inSchemaList = false
                    result.add(cleanLine)
                }
                continue
            }
            result.add(cleanLine)
        }

        if (!schemaListInserted) {
            if (!inPatch) {
                result.add("patch:")
                schemaListIndent = 2
            }
            insertSchemaList(result, schemaListIndent, schemaIds)
        }
        return result.joinToString("\n")
    }

    private fun insertSchemaList(lines: MutableList<String>, baseIndent: Int, schemaIds: List<String>) {
        val indent = " ".repeat(baseIndent)
        val itemIndent = " ".repeat(baseIndent + 2)
        lines.add("${indent}schema_list:")
        schemaIds.forEach { id -> lines.add("$itemIndent- schema: $id") }
    }
}
