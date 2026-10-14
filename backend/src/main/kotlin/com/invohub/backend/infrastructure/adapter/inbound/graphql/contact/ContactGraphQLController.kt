package com.invohub.backend.infrastructure.adapter.inbound.graphql.contact

import com.invohub.backend.domain.auth.AuthenticatedUser
import com.invohub.backend.domain.common.TSIDCodec
import com.invohub.backend.domain.contact.Contact
import com.invohub.backend.domain.contact.ContactInput
import com.invohub.backend.domain.contact.ContactKind
import com.invohub.backend.domain.contact.ContactNotFoundException
import com.invohub.backend.domain.contact.ContactService
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.AddressView
import com.invohub.backend.infrastructure.adapter.inbound.graphql.company.CompanyScope
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

data class ContactView(
    val id: String,
    val kind: ContactKind,
    val name: String,
    val country: String,
    val taxId: String?,
    val email: String?,
    val phone: String?,
    val address: AddressView,
    val notes: String?,
    /** ISO-8601 */
    val archivedAt: String?,
) {
    companion object {
        fun of(contact: Contact) =
            ContactView(
                id = contact.getPublicId(),
                kind = contact.kind,
                name = contact.name,
                country = contact.country,
                taxId = contact.taxId,
                email = contact.email,
                phone = contact.phone,
                address = AddressView(contact.street, contact.city, contact.postalCode),
                notes = contact.notes,
                archivedAt = contact.archivedAt?.toString(),
            )
    }
}

data class ContactPageView(
    val contacts: List<ContactView>,
    val total: Long,
)

@Controller
class ContactGraphQLController(
    private val contactService: ContactService,
    private val companyScope: CompanyScope,
) {
    @QueryMapping
    fun contacts(
        @Argument companyId: String,
        @Argument query: String?,
        @Argument archived: Boolean,
        @Argument offset: Int,
        @Argument limit: Int,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ContactPageView {
        val page = contactService.search(companyScope.enter(principal, companyId), query, archived, offset, limit)
        return ContactPageView(page.contacts.map(ContactView::of), page.total)
    }

    @QueryMapping
    fun contact(
        @Argument companyId: String,
        @Argument contactId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ContactView = ContactView.of(contactService.get(companyScope.enter(principal, companyId), contactId(contactId)))

    @MutationMapping
    fun createContact(
        @Argument companyId: String,
        @Argument input: ContactInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ContactView = ContactView.of(contactService.create(companyScope.enter(principal, companyId), input))

    @MutationMapping
    fun updateContact(
        @Argument companyId: String,
        @Argument contactId: String,
        @Argument input: ContactInput,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ContactView =
        ContactView.of(contactService.update(companyScope.enter(principal, companyId), contactId(contactId), input))

    @MutationMapping
    fun archiveContact(
        @Argument companyId: String,
        @Argument contactId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ContactView =
        ContactView.of(contactService.archive(companyScope.enter(principal, companyId), contactId(contactId)))

    @MutationMapping
    fun restoreContact(
        @Argument companyId: String,
        @Argument contactId: String,
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): ContactView =
        ContactView.of(contactService.restore(companyScope.enter(principal, companyId), contactId(contactId)))

    private fun contactId(publicId: String) =
        runCatching { TSIDCodec.decode(publicId) }.getOrNull() ?: throw ContactNotFoundException()
}
