package com.invohub.backend

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class GreetingController {
    @QueryMapping
    fun greeting(
        @Argument name: String?,
    ): String {
        val targetName = name ?: "World"
        return "Hello $targetName!"
    }
}
