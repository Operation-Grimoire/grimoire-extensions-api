package io.grimoire.api.util

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsoupExtensionsTest {

    private fun p(html: String, baseUri: String = "") =
        Jsoup.parseBodyFragment("<p>$html</p>", baseUri).selectFirst("p")!!

    @Test
    fun `plain text is escaped and otherwise unchanged`() {
        assertEquals("hello world", p("hello world").richHtml())
    }

    @Test
    fun `html-significant characters in text are escaped`() {
        // Jsoup decodes &amp; in the source to &; richHtml re-escapes it.
        assertEquals("a &amp; b &lt; c", p("a &amp; b &lt; c").richHtml())
    }

    @Test
    fun `br is emitted as a self-contained tag`() {
        assertEquals("a<br>b", p("a<br>b").richHtml())
    }

    @Test
    fun `em maps to italic`() {
        assertEquals("<i>thinking</i>", p("<em>thinking</em>").richHtml())
    }

    @Test
    fun `i maps to italic too`() {
        assertEquals("<i>thinking</i>", p("<i>thinking</i>").richHtml())
    }

    @Test
    fun `strong maps to bold`() {
        assertEquals("<b>loud</b>", p("<strong>loud</strong>").richHtml())
    }

    @Test
    fun `b maps to bold too`() {
        assertEquals("<b>loud</b>", p("<b>loud</b>").richHtml())
    }

    @Test
    fun `u maps to underline`() {
        assertEquals("<u>under</u>", p("<u>under</u>").richHtml())
    }

    @Test
    fun `nested em and strong nest correctly`() {
        assertEquals("<i>a<b>b</b>c</i>", p("<em>a<strong>b</strong>c</em>").richHtml())
    }

    @Test
    fun `a with absolutised href`() {
        val el = p("""<a href="/foo">click</a>""", baseUri = "https://example.com/")
        assertEquals("""<a href="https://example.com/foo">click</a>""", el.richHtml())
    }

    @Test
    fun `a without href is unwrapped`() {
        assertEquals("naked", p("<a>naked</a>").richHtml())
    }

    @Test
    fun `inline text around tags keeps a single space`() {
        // Mirrors Jsoup's normalisation: surrounding whitespace collapses,
        // and the result reads the same as element.text().
        assertEquals("a <b>b</b> <i>c</i>", p("a <strong>b</strong> <em>c</em>").richHtml())
    }

    @Test
    fun `margin-left 15px after br emits two nbsp`() {
        assertEquals(
            """a<br>&nbsp;&nbsp;b""",
            p("""a<br><span style="margin-left: 15px">b</span>""").richHtml(),
        )
    }

    @Test
    fun `margin-left 30px after br emits four nbsp`() {
        assertEquals(
            """a<br>&nbsp;&nbsp;&nbsp;&nbsp;b""",
            p("""a<br><span style="margin-left: 30px">b</span>""").richHtml(),
        )
    }

    @Test
    fun `margin-left 45px after br emits six nbsp`() {
        assertEquals(
            """a<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;b""",
            p("""a<br><span style="margin-left: 45px">b</span>""").richHtml(),
        )
    }

    @Test
    fun `runaway margin-left is capped at eight indent levels`() {
        val expected = "a<br>" + "&nbsp;".repeat(16) + "b"
        assertEquals(
            expected,
            p("""a<br><span style="margin-left: 9999px">b</span>""").richHtml(),
        )
    }

    @Test
    fun `margin-left inside em still produces nbsp indent`() {
        // The previous tail-check heuristic regressed this; explicit
        // line-start tracking lets us cross inline wrappers.
        assertEquals(
            "a<br><i>&nbsp;&nbsp;b</i>",
            p("""a<br><em><span style="margin-left: 15px">b</span></em>""").richHtml(),
        )
    }

    @Test
    fun `span without margin-left is unwrapped`() {
        assertEquals(
            """a<br>b""",
            p("""a<br><span class="foo">b</span>""").richHtml(),
        )
    }

    @Test
    fun `unparseable margin-left value is ignored`() {
        assertEquals(
            "a<br>b",
            p("""a<br><span style="margin-left: abc">b</span>""").richHtml(),
        )
    }

    @Test
    fun `output is trimmed of surrounding whitespace`() {
        assertEquals("hello", p("  hello  ").richHtml())
    }

    @Test
    fun `pretty-printed soul board indents each row`() {
        // Real lightnovelstranslations markup formats each <br>/<span> on
        // its own line in the source HTML, so Jsoup sees stray newline
        // text nodes between siblings. richHtml must absorb those at
        // line-start rather than emitting them as literal spaces.
        val el = p(
            """
            【Vitality】<br>
            <span style="margin-left: 15px">【Stamina】5</span><br>
            <span style="margin-left: 15px">【Immunity】</span><br>
            <span style="margin-left: 30px">【Magic Resistance】1</span>
            """.trimIndent(),
        )
        val expected = buildString {
            append("【Vitality】<br>")
            append("&nbsp;&nbsp;【Stamina】5<br>")
            append("&nbsp;&nbsp;【Immunity】<br>")
            append("&nbsp;&nbsp;&nbsp;&nbsp;【Magic Resistance】1")
        }
        assertEquals(expected, el.richHtml())
    }

    @Test
    fun `script and other unknown blocks are unwrapped`() {
        // Children get walked even if we don't recognise the tag — useful
        // for `<div>`/`<section>` wrappers, fragile for `<script>`/`<style>`
        // but those are unusual inside a story <p>. If they ever appear,
        // the caller's selector should keep them out.
        assertEquals("hello", p("<div>hello</div>").richHtml())
    }
}
