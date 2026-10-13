package com.invohub.backend.infrastructure.adapter.inbound.graphql

import com.invohub.backend.domain.auth.EmailDeliveryException
import com.invohub.backend.domain.common.InvalidInputException
import com.invohub.backend.domain.common.NotAllowedException
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.company.BankAccountNotFoundException
import com.invohub.backend.domain.company.CompanyClosedException
import com.invohub.backend.domain.company.CompanyNotFoundException
import com.invohub.backend.domain.company.CompanyPermissionDeniedException
import com.invohub.backend.domain.company.CompanyReadOnlyException
import com.invohub.backend.domain.company.CompanySuspendedException
import com.invohub.backend.domain.company.MemberNotFoundException
import com.invohub.backend.domain.company.OwnershipTransferNotFoundException
import com.invohub.backend.domain.invitation.InvitationNotFoundException
import com.invohub.backend.domain.invitation.InvitationRefusedException
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
            is NotAllowedException ->
                GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.FORBIDDEN)
                    .message("Not allowed: ${ex.reason}")
                    .extensions(mapOf("code" to "not_allowed", "reason" to ex.reason))
                    .build()
            is InvitationRefusedException ->
                GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("Invitation refused: ${ex.reason}")
                    .extensions(
                        mapOf(
                            "code" to "invitation_refused",
                            "reason" to ex.reason,
                            "companyId" to ex.companyId?.let(TSIDCodec::encode),
                        ).filterValues { it != null },
                    ).build()
            is EmailDeliveryException ->
                error(env, ErrorType.INTERNAL_ERROR, "The email couldn't be sent", "email_delivery_failed")
            is BankAccountNotFoundException ->
                error(env, ErrorType.NOT_FOUND, "Bank account not found", "bank_account_not_found")
            is InvitationNotFoundException ->
                error(env, ErrorType.NOT_FOUND, "Invitation not found", "invitation_not_found")
            is MemberNotFoundException -> error(env, ErrorType.NOT_FOUND, "Member not found", "member_not_found")
            is CompanyNotFoundException -> error(env, ErrorType.NOT_FOUND, "Company not found", "not_found")
            is CompanyPermissionDeniedException ->
                error(env, ErrorType.FORBIDDEN, "Your role in this company doesn't allow this", "forbidden")
            is CompanyReadOnlyException ->
                error(env, ErrorType.FORBIDDEN, "The company is read-only", "company_read_only")
            is CompanyClosedException -> error(env, ErrorType.FORBIDDEN, "The company is closed", "company_closed")
            is OwnershipTransferNotFoundException ->
                error(env, ErrorType.NOT_FOUND, "Ownership transfer not found", "ownership_transfer_not_found")
            is CompanySuspendedException ->
                error(env, ErrorType.FORBIDDEN, "The company is suspended", "company_suspended")
            else -> null
        }

    private fun error(
        env: DataFetchingEnvironment,
        type: ErrorType,
        message: String,
        code: String,
    ): GraphQLError =
        GraphqlErrorBuilder
            .newError(env)
            .errorType(type)
            .message(message)
            .extensions(mapOf("code" to code))
            .build()
}
