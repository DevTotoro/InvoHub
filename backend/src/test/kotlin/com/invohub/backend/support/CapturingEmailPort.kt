package com.invohub.backend.support

import com.invohub.backend.domain.auth.EmailPort
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** Records magic links instead of sending them. Shared by every test in the cached context, so clear it. */
@Primary
@TestComponent
class CapturingEmailPort : EmailPort {
    data class SentMagicLink(
        val email: String,
        val rawToken: String,
    )

    val sent = CopyOnWriteArrayList<SentMagicLink>()

    override fun sendMagicLink(
        email: String,
        rawToken: String,
    ) {
        sent += SentMagicLink(email, rawToken)
    }

    fun clear() = sent.clear()
}
