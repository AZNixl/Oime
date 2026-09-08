package com.azime.input.core.rime

import java.io.File

/**
 * default.custom.yaml 安全补丁工具：只修改 patch.schema_list，保留用户的其他配置。
 *
 * 背景：
 * - trime/trime2 通过 JNI selectRimeSchemas() 设置启用方案，不碰 default.custom.yaml
 * - xime RimeEngine 缺该 API，暂时通过修改 yaml 实现
 * - 必须保留用户的其他 patch（switcher/menu/key_binder 等），只改 schema_list
 */
object YamlPatcher {

    /**
     * 更新 default.custom.yaml 的 schema_list，保留其他 patch 内容。
     *
     * @param file default.custom.yaml 文件对象
     * @param schemaIds 新的 schema_list（按优先级排序，首个为默认方案）
     * @return 是否发生了修改
     */
    fun patchSchemaList(file: File, schemaIds: List<String>): Boolean {
        val existingContent = if (file.exists()) file.readText() else ""
        val newContent = buildPatchedYaml(existingContent, schemaIds)
        if (existingContent == newContent) return false
        file.writeText(newContent)
        return true
    }

    private fun buildPatchedYaml(original: String, schemaIds: List<String>): String {
        // 策略：
        // 1. 保留 # 注释和非 patch: 顶级键
        // 2. 保留 patch: 下除 schema_list 外的其他键
        // 3. 替换或插入 schema_list
        // 轮17.1 修复：处理 Windows 换行符（\r\n）

        val lines = original.lines()
        val result = mutableListOf<String>()
        var inPatch = false
        var inSchemaList = false
        var schemaListIndent = 0
        var schemaListInserted = false

        for (line in lines) {
            // 移除 \r（Windows 换行符）
            val cleanLine = line.removeSuffix("\r")
            val trimmed = cleanLine.trimStart()
            
            // 注释和空行保留
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                result.add(cleanLine)
                continue
            }

            // 顶级 patch: 键
            if (cleanLine.startsWith("patch:")) {
                inPatch = true
                result.add(cleanLine)
                continue
            }

            // patch: 下的 schema_list: 键
            if (inPatch && trimmed.startsWith("schema_list:")) {
                inSchemaList = true
                schemaListIndent = cleanLine.indexOf("schema_list:")
                // 跳过旧的 schema_list 内容，稍后插入新的
                continue
            }

            // schema_list 子项（以 - schema: 或 - { 开头，或缩进更深的 YAML）
            if (inSchemaList) {
                val indent = cleanLine.takeWhile { it.isWhitespace() }.length
                // 如果缩进回退到 schema_list 同级或更浅，说明 schema_list 结束
                if (trimmed.isNotEmpty() && indent <= schemaListIndent) {
                    // 插入新 schema_list
                    if (!schemaListInserted) {
                        insertSchemaList(result, schemaListIndent, schemaIds)
                        schemaListInserted = true
                    }
                    inSchemaList = false
                    result.add(cleanLine)
                }
                // 否则跳过旧 schema_list 的内容
                continue
            }

            // 其他行保留
            result.add(cleanLine)
        }

        // 如果遍历结束仍未插入 schema_list（原文件无 patch: 或 patch: 下无 schema_list）
        if (!schemaListInserted) {
            if (!inPatch) {
                // 无 patch: → 添加整个 patch 块
                result.add("patch:")
                schemaListIndent = 2
            }
            insertSchemaList(result, schemaListIndent, schemaIds)
        }

        return result.joinToString("\n")
    }

    private fun insertSchemaList(
        lines: MutableList<String>,
        baseIndent: Int,
        schemaIds: List<String>
    ) {
        val indent = " ".repeat(baseIndent)
        val itemIndent = " ".repeat(baseIndent + 2)
        lines.add("${indent}schema_list:")
        schemaIds.forEach { id ->
            lines.add("$itemIndent- schema: $id")
        }
    }
}
