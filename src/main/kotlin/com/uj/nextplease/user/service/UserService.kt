package com.uj.nextplease.user.service

import com.uj.nextplease.user.User
import com.uj.nextplease.user.model.DoctorResponse
import com.uj.nextplease.user.model.RegisterDoctorRequest
import com.uj.nextplease.user.model.UserDetails
import com.uj.nextplease.user.model.UserRole
import com.uj.nextplease.user.model.UserStatus
import com.uj.nextplease.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun findByEmail(email: String): UserDetails? {
        val user = userRepository.findByEmail(email)
        return toUserDetails(user ?: return null)
    }

    fun isPasswordCorrect(
        password: String,
        encodedPassword: String,
    ): Boolean = passwordEncoder.matches(password, encodedPassword)

    /**
     * Self-service doctor registration. The requester sets their own password and the account
     * starts [UserStatus.PENDING] until an admin approves it. The result is intentionally neutral:
     * if the email already exists we silently skip creation so callers can't probe registrations.
     */
    @Transactional
    fun registerDoctor(request: RegisterDoctorRequest) {
        if (userRepository.findByEmail(request.email) != null) {
            return
        }

        userRepository.save(
            User(
                email = request.email,
                password = passwordEncoder.encode(request.password)!!,
                role = UserRole.DOCTOR.name,
                name = request.name,
                surname = request.surname,
                status = UserStatus.PENDING,
            ),
        )
    }

    fun getPendingDoctors(): List<DoctorResponse> =
        userRepository.findByRoleAndStatus(UserRole.DOCTOR.name, UserStatus.PENDING).map(::toDoctorResponse)

    fun getAllDoctors(): List<DoctorResponse> = userRepository.findByRole(UserRole.DOCTOR.name).map(::toDoctorResponse)

    @Transactional
    fun approveDoctor(id: Long): DoctorResponse? {
        val user = userRepository.findById(id).orElse(null) ?: return null
        if (user.role != UserRole.DOCTOR.name || user.status != UserStatus.PENDING) {
            throw IllegalStateException("Only pending doctors can be approved")
        }

        user.status = UserStatus.ACTIVE
        return toDoctorResponse(userRepository.save(user))
    }

    @Transactional
    fun rejectDoctor(id: Long): Boolean {
        val user = userRepository.findById(id).orElse(null) ?: return false
        if (user.role != UserRole.DOCTOR.name || user.status != UserStatus.PENDING) {
            throw IllegalStateException("Only pending doctors can be rejected")
        }

        userRepository.delete(user)
        return true
    }

    /**
     * Hard-deletes any user. Guards: an admin cannot delete their own account, and the last
     * remaining active admin cannot be removed.
     */
    @Transactional
    fun deleteUser(
        id: Long,
        requesterEmail: String,
    ): Boolean {
        val user = userRepository.findById(id).orElse(null) ?: return false

        if (user.email == requesterEmail) {
            throw IllegalStateException("You cannot delete your own account")
        }

        if (user.role == UserRole.ADMIN.name && user.status == UserStatus.ACTIVE) {
            val activeAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN.name, UserStatus.ACTIVE)
            if (activeAdmins <= 1) {
                throw IllegalStateException("Cannot delete the last active admin")
            }
        }

        userRepository.delete(user)
        return true
    }

    private fun toUserDetails(user: User): UserDetails =
        UserDetails(
            id = user.id!!,
            email = user.email,
            password = user.password,
            role = user.role,
            name = user.name,
            surname = user.surname,
            status = user.status,
        )

    private fun toDoctorResponse(user: User): DoctorResponse =
        DoctorResponse(
            id = user.id!!,
            email = user.email,
            name = user.name,
            surname = user.surname,
            status = user.status,
        )
}
