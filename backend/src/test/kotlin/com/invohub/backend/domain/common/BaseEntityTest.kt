package com.invohub.backend.domain.common

import com.invohub.backend.domain.user.User
import com.invohub.backend.domain.user.UserRepository
import com.invohub.backend.support.IntegrationTest
import jakarta.persistence.EntityManager
import org.hibernate.proxy.HibernateProxy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@IntegrationTest
class BaseEntityTest {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun clean() = userRepository.deleteAll()

    @Test
    fun `id is assigned at construction and unchanged by saving`() {
        val user = User("jane@example.com")
        val idBeforeSave = user.id

        val saved = userRepository.save(user)

        assertNotEquals(0L, idBeforeSave)
        assertEquals(idBeforeSave, saved.id)
    }

    @Test
    fun `different unsaved entities are not equal`() {
        assertNotEquals(User("jane@example.com"), User("john@example.com"))
    }

    @Test
    fun `an entity in a hash set is still found after saving`() {
        val user = User("jane@example.com")
        val set = hashSetOf(user)

        userRepository.save(user)

        assertTrue(user in set)
    }

    @Test
    fun `spring data treats unsaved entities as new and saved ones as existing`() {
        val info = JpaEntityInformationSupport.getEntityInformation(User::class.java, entityManager)
        val user = User("jane@example.com")

        assertTrue(info.isNew(user))
        assertFalse(info.isNew(userRepository.save(user)))
    }

    @Test
    fun `a lazy proxy equals the entity it stands for`() {
        val user = userRepository.save(User("jane@example.com"))

        transactionTemplate.executeWithoutResult {
            val proxy = entityManager.getReference(User::class.java, user.id)

            assertIs<HibernateProxy>(proxy)
            assertEquals(user, proxy)
            assertEquals(proxy, user)
            assertEquals(user.hashCode(), proxy.hashCode())
        }
    }

    @Test
    fun `audit timestamps are set on save`() {
        val user = userRepository.save(User("jane@example.com"))

        assertEquals(user.createdAt, user.updatedAt)
    }

    @Test
    fun `concurrent updates are rejected by optimistic locking`() {
        val user = userRepository.save(User("jane@example.com"))
        val first = userRepository.findById(user.id).get()
        val second = userRepository.findById(user.id).get()

        first.lock("first")
        userRepository.save(first)
        second.lock("second")

        assertFailsWith<OptimisticLockingFailureException> { userRepository.save(second) }
    }
}
