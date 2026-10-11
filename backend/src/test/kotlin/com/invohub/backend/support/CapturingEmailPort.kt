package com.invohub.backend.support

import com.invohub.backend.domain.auth.EmailPort
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Primary
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/** Records magic links instead of sending them. Shared by every test in the cached context, so clear it. */
@Primary
@TestComponent
class CapturingEmailPort : EmailPort {
    data class SentMagicLink(
        val email: String,
        val link: URI,
    ) {
        val rawToken: String
            get() =
                UriComponentsBuilder
                    .fromUri(link)
                    .build()
                    .queryParams
                    .getFirst("token")!!
    }

    val sent = CopyOnWriteArrayList<SentMagicLink>()

    override fun sendMagicLink(
        email: String,
        link: URI,
        validFor: Duration,
    ) {
        sent += SentMagicLink(email, link)
    }

    fun clear() = sent.clear()
}
