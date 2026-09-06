package com.licode.li63050a6.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

/**
 * 轻量 Markdown 渲染：代码块 / 行内代码 / 粗体 / 斜体 / 标题 / 链接。
 * 仅用于对话展示，不引入完整 markdown 解析依赖（保持 minSdk 21 与打包体积）。
 */
object Markdown {

    fun render(text: String, codeBackground: Color, linkColor: Color): AnnotatedString {
        val lines = text.split("\n")
        val b = AnnotatedString.Builder()
        var inCode = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inCode = !inCode
                continue
            }
            if (inCode) {
                if (b.length > 0) b.append("\n")
                b.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                    append(line)
                }
                continue
            }
            appendLineStyled(b, line, codeBackground, linkColor)
        }
        return b.toAnnotatedString()
    }

    private fun appendLineStyled(
        b: AnnotatedString.Builder,
        line: String,
        codeBackground: Color,
        linkColor: Color,
    ) {
        val t = line.trim()

        // 标题
        if (t.startsWith("#")) {
            val level = t.takeWhile { it == '#' }.length
            val body = t.drop(level).trim()
            if (b.length > 0) b.append("\n")
            b.withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = TextUnit(20f - (level - 1) * 2f, TextUnitType.Sp),
                ),
            ) { append(body) }
            b.append("\n")
            return
        }
        // 引用
        if (t.startsWith(">")) {
            if (b.length > 0) b.append("\n")
            b.withStyle(SpanStyle(color = Color.Gray)) { append(t.drop(1).trim()) }
            b.append("\n")
            return
        }
        // 无序列表
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) {
            appendInline(b, "• " + t.drop(2), codeBackground, linkColor)
            return
        }
        // 有序列表
        val num = t.takeWhile { it.isDigit() }
        if (num.isNotEmpty() && t.drop(num.length).startsWith(". ")) {
            appendInline(b, t, codeBackground, linkColor)
            return
        }

        if (b.length > 0) b.append("\n")
        appendInline(b, line, codeBackground, linkColor)
    }

    /** 解析行内 `代码` / **粗体** / *斜体* / [链接](网址)。 */
    private fun appendInline(
        b: AnnotatedString.Builder,
        line: String,
        codeBackground: Color,
        linkColor: Color,
    ) {
        var i = 0
        val n = line.length
        while (i < n) {
            val c = line[i]
            when {
                c == '`' -> {
                    val end = line.indexOf('`', i + 1)
                    if (end > i) {
                        b.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                            append(line.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        b.append(c); i++
                    }
                }
                c == '[' -> {
                    val close = line.indexOf("](", i)
                    val end = if (close > i) line.indexOf(')', close + 2) else -1
                    if (close > i && end > close) {
                        val label = line.substring(i + 1, close)
                        val url = line.substring(close + 2, end)
                        b.withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(label)
                        }
                        b.append(" ($url)")
                        i = end + 1
                    } else {
                        b.append(c); i++
                    }
                }
                c == '*' && i + 1 < n && line[i + 1] == '*' -> {
                    val close = line.indexOf("**", i + 2)
                    if (close > i) {
                        b.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(line.substring(i + 2, close))
                        }
                        i = close + 2
                    } else {
                        b.append(c); i++
                    }
                }
                c == '*' -> {
                    val close = line.indexOf('*', i + 1)
                    val next = line.indexOf("**", i)
                    if (close > i && (next == -1 || next >= close)) {
                        b.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(line.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        b.append(c); i++
                    }
                }
                else -> {
                    b.append(c); i++
                }
            }
        }
        if (b.length > 0 && !line.endsWith("\n")) b.append("\n")
    }
}