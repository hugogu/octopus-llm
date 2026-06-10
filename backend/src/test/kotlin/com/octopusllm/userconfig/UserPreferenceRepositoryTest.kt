package com.octopusllm.userconfig

import com.octopusllm.auth.User
import com.octopusllm.auth.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserPreferenceRepositoryTest {

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `findByUserId returns preference when exists`() {
        val user = userRepository.save(User(email = "pref1@example.com", passwordHash = "hash", emailVerified = true))
        val preference = UserPreference(
            user = user,
            lastSelectedModelId = "gpt-4o",
            themePreference = "dark",
            sidebarCollapsed = true,
        )
        userPreferenceRepository.save(preference)

        val found = userPreferenceRepository.findByUserId(user.id)
        assert(found != null)
        assert(found?.lastSelectedModelId == "gpt-4o")
        assert(found?.themePreference == "dark")
        assert(found?.sidebarCollapsed == true)
    }

    @Test
    fun `findByUserId returns null when not exists`() {
        val user = userRepository.save(User(email = "pref2@example.com", passwordHash = "hash", emailVerified = true))
        
        val found = userPreferenceRepository.findByUserId(user.id)
        assert(found == null)
    }

    @Test
    fun `preference can be updated`() {
        val user = userRepository.save(User(email = "pref3@example.com", passwordHash = "hash", emailVerified = true))
        val preference = UserPreference(user = user, lastSelectedModelId = "claude-3")
        userPreferenceRepository.save(preference)

        preference.lastSelectedModelId = "gpt-4"
        preference.themePreference = "light"
        userPreferenceRepository.save(preference)

        val updated = userPreferenceRepository.findByUserId(user.id)
        assert(updated?.lastSelectedModelId == "gpt-4")
        assert(updated?.themePreference == "light")
    }

    @Test
    fun `cascading delete with user`() {
        val user = userRepository.save(User(email = "pref4@example.com", passwordHash = "hash", emailVerified = true))
        val preference = UserPreference(user = user)
        userPreferenceRepository.save(preference)

        assert(userPreferenceRepository.findByUserId(user.id) != null)
        
        userPreferenceRepository.delete(preference)
        userRepository.delete(user)
        
        assert(userPreferenceRepository.findByUserId(user.id) == null)
    }
}
