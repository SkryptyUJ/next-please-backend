package com.uj.nextplease.user.service

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.user.User
import com.uj.nextplease.user.model.RegisterDoctorRequest
import com.uj.nextplease.user.model.UserRole
import com.uj.nextplease.user.model.UserStatus
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class UserServiceIntegrationTest(
    @Autowired private val userService: UserService,
    @Autowired private val userRepository: UserRepository,
) {
    @BeforeEach
    fun cleanDatabase() {
        userRepository.deleteAllInBatch()
    }

    @Test
    fun `given a registration when registerDoctor then it creates a pending doctor with a hashed password`() {
        userService.registerDoctor(
            RegisterDoctorRequest(email = "new@clinic.com", name = "New", surname = "Doc", password = "password123"),
        )

        val saved = userRepository.findByEmail("new@clinic.com")
        assertThat(saved).isNotNull()
        assertThat(saved?.role).isEqualTo(UserRole.DOCTOR.name)
        assertThat(saved?.status).isEqualTo(UserStatus.PENDING)
        assertThat(saved?.password).isNotEqualTo("password123")
        assertThat(userService.isPasswordCorrect("password123", saved!!.password)).isTrue()
    }

    @Test
    fun `given an existing email when registerDoctor then it does not create a duplicate`() {
        userRepository.save(activeDoctor("dup@clinic.com"))

        userService.registerDoctor(
            RegisterDoctorRequest(email = "dup@clinic.com", name = "New", surname = "Doc", password = "password123"),
        )

        assertThat(userRepository.findByRole(UserRole.DOCTOR.name)).hasSize(1)
    }

    @Test
    fun `given a pending doctor when approveDoctor then it becomes active`() {
        val pending = userRepository.save(pendingDoctor("pending@clinic.com"))

        val approved = userService.approveDoctor(pending.id!!)

        assertThat(approved?.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(userRepository.findById(pending.id!!).get().status).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    fun `given an already active doctor when approveDoctor then it throws IllegalStateException`() {
        val active = userRepository.save(activeDoctor("active@clinic.com"))

        assertThatThrownBy { userService.approveDoctor(active.id!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `given a pending doctor when rejectDoctor then the row is deleted`() {
        val pending = userRepository.save(pendingDoctor("reject@clinic.com"))

        val result = userService.rejectDoctor(pending.id!!)

        assertThat(result).isTrue()
        assertThat(userRepository.findByEmail("reject@clinic.com")).isNull()
    }

    @Test
    fun `given an active doctor when rejectDoctor then it throws IllegalStateException`() {
        val active = userRepository.save(activeDoctor("active@clinic.com"))

        assertThatThrownBy { userService.rejectDoctor(active.id!!) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `given another user when deleteUser then it is hard-deleted`() {
        userRepository.save(activeAdmin("admin@clinic.com"))
        val doctor = userRepository.save(activeDoctor("victim@clinic.com"))

        val result = userService.deleteUser(doctor.id!!, "admin@clinic.com")

        assertThat(result).isTrue()
        assertThat(userRepository.findByEmail("victim@clinic.com")).isNull()
    }

    @Test
    fun `given the caller's own account when deleteUser then it throws IllegalStateException`() {
        val admin = userRepository.save(activeAdmin("admin@clinic.com"))
        userRepository.save(activeAdmin("admin2@clinic.com"))

        assertThatThrownBy { userService.deleteUser(admin.id!!, "admin@clinic.com") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `given the last active admin when deleteUser then it throws IllegalStateException`() {
        val onlyAdmin = userRepository.save(activeAdmin("admin@clinic.com"))
        val otherAdmin = userRepository.save(activeAdmin("admin2@clinic.com"))

        userService.deleteUser(otherAdmin.id!!, "admin@clinic.com")

        assertThatThrownBy { userService.deleteUser(onlyAdmin.id!!, "admin2@clinic.com") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(userRepository.findByEmail("admin@clinic.com")).isNotNull()
    }

    private fun pendingDoctor(email: String): User =
        User(email = email, password = "encoded", role = UserRole.DOCTOR.name, name = "Pen", surname = "Ding", status = UserStatus.PENDING)

    private fun activeDoctor(email: String): User =
        User(email = email, password = "encoded", role = UserRole.DOCTOR.name, name = "Doc", surname = "Tor", status = UserStatus.ACTIVE)

    private fun activeAdmin(email: String): User =
        User(email = email, password = "encoded", role = UserRole.ADMIN.name, name = "Ad", surname = "Min", status = UserStatus.ACTIVE)
}
