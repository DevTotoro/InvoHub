package com.invohub.backend.support

import jakarta.servlet.http.Cookie
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import tools.jackson.databind.json.JsonMapper

private val jsonMapper = JsonMapper()

/**
 * Runs a GraphQL operation through MockMvc, signed in with [session] if given. Spring GraphQL answers
 * asynchronously; requests that security rejects never get that far, so both cases are handled.
 */
fun MockMvc.graphql(
    session: Cookie?,
    query: String,
    variables: Map<String, Any?> = emptyMap(),
): ResultActionsDsl {
    val result =
        post("/api/graphql") {
            with(csrf())
            session?.let { cookie(it) }
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(mapOf("query" to query, "variables" to variables))
        }.andReturn()
    val actions = if (result.request.isAsyncStarted) perform(asyncDispatch(result)) else Completed(result)
    return ResultActionsDsl(actions, this)
}

/** Signs in through the real magic link flow and returns the session cookie. */
fun MockMvc.signIn(
    emailPort: CapturingEmailPort,
    email: String,
): Cookie {
    post("/api/auth/request") {
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"email": "$email"}"""
    }.andExpect { status { isOk() } }
    val token = emailPort.sent.last { it.email == email }.rawToken

    return post("/api/auth/verify") {
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"token": "$token"}"""
    }.andExpect { status { isNoContent() } }
        .andReturn()
        .response
        .getCookie("SESSION")!!
}

private class Completed(
    private val result: MvcResult,
) : ResultActions {
    override fun andExpect(matcher: ResultMatcher) = apply { matcher.match(result) }

    override fun andDo(handler: ResultHandler) = apply { handler.handle(result) }

    override fun andReturn() = result
}
