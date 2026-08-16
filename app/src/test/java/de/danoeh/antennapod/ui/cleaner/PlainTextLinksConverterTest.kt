package de.danoeh.antennapod.ui.cleaner


import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import de.danoeh.antennapod.ui.cleaner.PlainTextLinksConverter.NOT_ALLOWED_END_CHARS
import org.junit.Test

class PlainTextLinksConverterTest {

    @Test
    fun testConvertPlainTextLinksToHtml() {
        val link1 = "https://url.to/link"
        val textWithLink = "text  " + link1
        assertEquals("text " + makeLinkHtml(link1), PlainTextLinksConverter.convertLinksToHtml(textWithLink))

        val link2 = "https://t.me/link"
        val textWithLink2 = "text " + link2
        assertEquals("text " + makeLinkHtml(link2), PlainTextLinksConverter.convertLinksToHtml(textWithLink2))

        val text = "artist here:  www.example.com"
        val expected = "artist here: <a href=\"https://www.example.com\">www.example.com</a>"
        assertEquals(expected, PlainTextLinksConverter.convertLinksToHtml(text))

        val textWithTwoLinks = "text " + link1 + " and " + link2
        val expectedTwoLinks = "text " + makeLinkHtml(link1) + " and " + makeLinkHtml(link2)
        assertEquals(expectedTwoLinks, PlainTextLinksConverter.convertLinksToHtml(textWithTwoLinks))

        val textWithMixturePlainTextAndHtml = "text " + link1 + " and " + makeLinkHtml(link2)
        val expectedMixture = "text " + makeLinkHtml(link1) + " and " + makeLinkHtml(link2)
        assertEquals(expectedMixture, PlainTextLinksConverter.convertLinksToHtml(textWithMixturePlainTextAndHtml))

        val textWithSpecialChars = "text'" + link1 + " and=" + link2
        val expectedWithSpecialChars = "text'" + makeLinkHtml(link1) + " and=" + makeLinkHtml(link2)
        assertEquals(expectedWithSpecialChars, PlainTextLinksConverter.convertLinksToHtml(textWithSpecialChars))

        val linkWithParams = "http://t.me/link#mark?param1=1&param2=true;param3=true"
        val textWithParams = "text " + linkWithParams + " after-text"
        val expectedWithParams = "text " + makeLinkHtml(linkWithParams) + " after-text"
        assertEquals(expectedWithParams, PlainTextLinksConverter.convertLinksToHtml(textWithParams))

        val linkWithComma = "https://example.org/%D0%%86_(%D1%%BC,_2020"
        val textWithComma = "text " + linkWithComma
        assertEquals("text " + makeLinkHtml(linkWithComma), PlainTextLinksConverter.convertLinksToHtml(textWithComma))

        val linkWithDot = "https://www.ietf.org/rfc/rfc3986.txt"
        val textWithDot = "text " + linkWithDot
        assertEquals("text " + makeLinkHtml(linkWithDot), PlainTextLinksConverter.convertLinksToHtml(textWithDot))

        val linkWithTilda = "https://www.example.org/valid/-~.,$/url/"
        val textWithTilda = "text " + linkWithTilda
        assertEquals("text " + makeLinkHtml(linkWithTilda), PlainTextLinksConverter.convertLinksToHtml(textWithTilda))

        val linkWithExclamation = "http://www.example.com/index.php?id=123&v=wall#!/index.php?id=234"
        val textWithExclamation = "text " + linkWithExclamation
        assertEquals("text " + makeLinkHtml(linkWithExclamation),
                PlainTextLinksConverter.convertLinksToHtml(textWithExclamation))

        val linkWithBrackets = "http://www.example.com/index.php?bar[]=1&bar[]=2"
        val textWithBrackets = "text " + linkWithBrackets
        assertEquals("text " + makeLinkHtml(linkWithBrackets),
                PlainTextLinksConverter.convertLinksToHtml(textWithBrackets))

        val linkWithAsterisk = "https://archive.org/web/*/http://www.example.com/"
        val textWithAsterisk = "text " + linkWithAsterisk
        assertEquals("text " + makeLinkHtml(linkWithAsterisk),
                PlainTextLinksConverter.convertLinksToHtml(textWithAsterisk))
    }

    @Test
    fun testBrokenLinksAreNotCreated() {
        val linkWithBrackets = "Sign up now (http://example.com/abc)"
        assertEquals(linkWithBrackets, PlainTextLinksConverter.convertLinksToHtml(linkWithBrackets))

        val linkWithBrackets2 = "Sign up now (http://example.com/abc)! please"
        assertEquals(linkWithBrackets2, PlainTextLinksConverter.convertLinksToHtml(linkWithBrackets2))

        val linkWithDot = "To read on, visit https://example.com."
        assertEquals(linkWithDot, PlainTextLinksConverter.convertLinksToHtml(linkWithDot))

        //we choose to ignore links like this, even though they are valid
        val validLinkIgnored = "Visit https://example.com/wiki_(url+rules)"
        assertEquals(validLinkIgnored, PlainTextLinksConverter.convertLinksToHtml(validLinkIgnored))

        val link = "https://example.com/abc"
        NOT_ALLOWED_END_CHARS.forEach { end ->
            assertEquals(link + end, PlainTextLinksConverter.convertLinksToHtml(link + end))
        }

        val firstLinkIgnored = "(" + link + ") and " + link
        assertEquals("(https://example.com/abc) and " + makeLinkHtml(link),
                PlainTextLinksConverter.convertLinksToHtml(firstLinkIgnored))

        val secondLinkIgnored = "text " + link + " and (" + link + ")"
        assertEquals("text " + makeLinkHtml(link) + " and (https://example.com/abc)",
                PlainTextLinksConverter.convertLinksToHtml(secondLinkIgnored))

        val middleLinkIgnored = "text " + link + " and (" + link + ") and " + link
        assertEquals("text " + makeLinkHtml(link) + " and (https://example.com/abc) and " + makeLinkHtml(link),
                PlainTextLinksConverter.convertLinksToHtml(middleLinkIgnored))
    }

    @Test
    fun testExistingLinksArePreserved() {
        val links = listOf(
                "Click <a alt=\"abc\" href=\"http://url.to/link\">http://url.to/link, this link</a>",
                "<a href=\"http://domain.org/link\">domain.org</a>",
                "you can find it on <a href=\"http://xy.org\">our new website http://xy.org</a>",
                "you can find it on <a href=\"http://xy.org/newlanding\">our new website http://xy.org</a>",
                "<p><img src=\"https://url.to/i.jpg\" alt=\"https://url.to/i.jpg\"></p>",
                "text \n<audio src=\"https://url.to/i.mp3\" alt=\"https://url.to/i.mp3\">\n  text \n</audio>",
                "<a href=\"https://example.com/p/ai-fakers?utm_source=example&amp;utm_medium=email\">AI interview</a> - <em>01:57:01</em>",
                "sign up for our premium feed here! <a href=\"https://www.example.com/url?q=https://example.com/join&amp;source=gmail-imap&amp;ust=123&amp;usg=AOvVaw123gzEv9s9\"><strong>https://example.com/join</strong></a>",
                "you can do so here:<a href=\"https://www.example.com/url?q=https://example.com/button&amp;source=gmail-imap&amp;ust=123&amp;usg=AOvV123jw--CX123tATY\"><strong>https://example.com/button</strong></a>",
                "LINKS:<a href=\"https://www.example.com/url?q=https://example.org/&amp;source=gmail-imap&amp;ust=123&amp;usg=AOvVa123GJxenALD\"><strong>Example</strong></a>",
                "<a href=\"https://www.example.com/url?q=https://example.org/buttons/ask-me-chili-cheese-fries&amp;source=gmail-imap&amp;ust=123&amp;usg=AOvVaw2oFNwzuvrfrokwHf6zq1P4\"><strong>Example</strong></a>",
                "<p><a href=\"https://example.com/media/FN_123zV2i?format=png&amp;name=900x900\">A picture of the photo in question</a></p>",
                "<a href=\"https://www.example.com/redirect?event=video_description&amp;redir_token=123l&amp;q=https%3A%2F%2Fexample.com%2Fshop%2Fbook%2F&amp;v=4iOzkYTrjzg\">https://example.com/shop/book/</a>",
                "<a href=\"https://www.example.com/redirect?event=video_description&amp;redir_token=123Ws&amp;q=https%3A%2F%2Fexample.me%2FyH6x%2Fgx5ywe7g&amp;v=yIbY7x5zQO8\">https://example.me/yH6x/gx5ywe7g</a>",
                ""
        )
        links.forEach { link -> assertEquals(link, PlainTextLinksConverter.convertLinksToHtml(link)) }
    }

    @Test
    fun testConvertToHtmlWhenNoLinksAreDetected() {
        assertNull(PlainTextLinksConverter.convertLinksToHtml(null))
        assertEquals("", PlainTextLinksConverter.convertLinksToHtml(""))

        val text = "plain text"
        assertEquals(text, PlainTextLinksConverter.convertLinksToHtml(text))

        val specialCharacters = "text with ' special \" characters !@#$%^&*()<>?123"
        var expected = specialCharacters.replace("&", "&amp;")
        expected = expected.replace("<", "&lt;")
        expected = expected.replace(">", "&gt;")
        assertEquals(expected, PlainTextLinksConverter.convertLinksToHtml(specialCharacters))

        val textWithDots = "\"Text With...Dots Works\""
        assertEquals(textWithDots, PlainTextLinksConverter.convertLinksToHtml(textWithDots))
    }

    /**
     * Adds `<a href>..</a>` around provided string
     */
    private fun makeLinkHtml(plain: String?): String {
        if (plain == null || plain.isEmpty()) {
            return ""
        }
        val encodedPlain = plain.replace("&", "&amp;")
        return "<a href=\"" + encodedPlain + "\">" + encodedPlain + "</a>"
    }
}
