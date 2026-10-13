package com.invohub.backend.infrastructure.adapter.outbound.email

import org.springframework.web.util.HtmlUtils
import java.net.URI

/**
 * One email, written once and rendered twice: as HTML with a button styled like the app's primary button, and
 * as plain text for clients that don't show HTML. Every text is escaped: company names and email addresses
 * are other people's words, never markup.
 */
data class EmailContent(
    val paragraphs: List<String>,
    /** Shown as a list, e.g. one line per invitation */
    val items: List<String> = emptyList(),
    val button: String,
    val link: URI,
    /** "Button not working? Copy this link" */
    val fallback: String,
    val footer: String? = null,
)

object EmailLayout {
    // The app's colours (Tailwind taupe-600 and the grays), as email clients need plain hex
    private const val BUTTON = "#5b4f4b"
    private const val TEXT = "#111827"
    private const val MUTED = "#6b7280"
    private const val FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"

    fun text(content: EmailContent): String =
        buildString {
            content.paragraphs.forEach { append(it).append("\n\n") }
            content.items.forEach { append("- ").append(it).append('\n') }
            if (content.items.isNotEmpty()) append('\n')
            append(content.button).append(": ").append(content.link)
            content.footer?.let { append("\n\n").append(it) }
        }

    fun html(content: EmailContent): String {
        val link = escape(content.link.toString())
        val paragraphs = content.paragraphs.joinToString("") { paragraph(escape(it)) }
        val items =
            if (content.items.isEmpty()) {
                ""
            } else {
                content.items.joinToString("", """<ul style="margin:0 0 16px;padding-left:20px;">""", "</ul>") {
                    """<li style="font-size:14px;line-height:22px;color:$TEXT;">${escape(it)}</li>"""
                }
            }
        // A table cell, not just a styled link: the way every email client shows a button reliably
        val button =
            """<table role="presentation" cellpadding="0" cellspacing="0" style="margin:8px 0 24px;"><tr>""" +
                """<td style="background:$BUTTON;">""" +
                """<a href="$link" style="display:inline-block;padding:10px 16px;font-family:$FONT;font-size:14px;""" +
                """font-weight:500;line-height:20px;color:#ffffff;text-decoration:none;">${escape(
                    content.button,
                )}</a>""" +
                "</td></tr></table>"
        val fallback =
            muted(
                escape(content.fallback) +
                    """<br><a href="$link" style="color:$MUTED;word-break:break-all;">$link</a>""",
            )
        val footer = content.footer?.let { muted(escape(it)) } ?: ""

        return "<!doctype html><html><head>" +
            """<meta charset="utf-8"><meta name="viewport" content="width=device-width"></head>""" +
            """<body style="margin:0;padding:24px;background:#ffffff;font-family:$FONT;color:$TEXT;">""" +
            """<table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="max-width:520px;">""" +
            "<tr><td>" +
            """<p style="margin:0 0 24px;font-size:20px;font-weight:700;">InvoHub</p>""" +
            paragraphs + items + button + fallback + footer +
            "</td></tr></table></body></html>"
    }

    private fun paragraph(html: String) =
        """<p style="margin:0 0 16px;font-size:14px;line-height:22px;color:$TEXT;">$html</p>"""

    private fun muted(html: String) =
        """<p style="margin:0 0 16px;font-size:12px;line-height:18px;color:$MUTED;">$html</p>"""

    private fun escape(text: String) = HtmlUtils.htmlEscape(text, "UTF-8")
}
