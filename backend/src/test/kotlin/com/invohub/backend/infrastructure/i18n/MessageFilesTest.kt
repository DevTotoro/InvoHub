package com.invohub.backend.infrastructure.i18n

import com.invohub.backend.domain.user.Language
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals

class MessageFilesTest {
    @Test
    fun `every language has every email text`() {
        val english = load("i18n/emails.properties").stringPropertyNames()

        Language.entries.filter { it != Language.EN }.forEach { language ->
            val translated = load("i18n/emails_${language.locale.language}.properties").stringPropertyNames()
            assertEquals(english, translated, "Texts in ${language.name} don't match the English ones")
        }
    }

    private fun load(path: String): Properties {
        val properties = Properties()
        MessageFilesTest::class.java.classLoader
            .getResourceAsStream(path)!!
            .reader(Charsets.UTF_8)
            .use(properties::load)
        return properties
    }
}
