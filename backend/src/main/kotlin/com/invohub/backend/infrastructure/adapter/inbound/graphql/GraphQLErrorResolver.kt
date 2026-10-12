package com.invohub.backend.infrastructure.adapter.inbound.graphql

import com.invohub.backend.domain.common.InvalidInputException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Turns domain exceptions into GraphQL errors with a stable `code` in `extensions` for clients to act on.
 * Anything not handled here becomes Spring GraphQL's generic INTERNAL_ERROR, without details.
 */
@Component
class GraphQLErrorResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? =
        when (ex) {
            is InvalidInputException ->
                GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("Invalid ${ex.field}: ${ex.reason}")
                    .extensions(mapOf("code" to "invalid_input", "field" to ex.field, "reason" to ex.reason))
                    .build()
            else -> null
        }
}
