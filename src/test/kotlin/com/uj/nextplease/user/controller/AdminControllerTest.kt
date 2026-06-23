package com.uj.nextplease.user.controller

import com.uj.nextplease.user.model.DoctorResponse
import com.uj.nextplease.user.model.UserStatus
import com.uj.nextplease.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class AdminControllerTest {
    private lateinit var userService: UserService
    private lateinit var adminController: AdminController

    @BeforeEach
    fun setUp() {
        userService = mock()
        adminController = AdminController(userService)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun setAuthentication(email: String) {
        val auth = UsernamePasswordAuthenticationToken(email, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    // --- getPendingDoctors ---

    @Test
    fun `getPendingDoctors returns 200 with the list of pending doctors`() {
        val doctors = listOf(doctorResponse(1L, UserStatus.PENDING), doctorResponse(2L, UserStatus.PENDING))
        whenever(userService.getPendingDoctors()).thenReturn(doctors)

        val response = adminController.getPendingDoctors()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(2)
        assertThat(response.body?.all { it.status == UserStatus.PENDING }).isTrue()
    }

    @Test
    fun `getPendingDoctors returns 200 with empty list when no pending doctors`() {
        whenever(userService.getPendingDoctors()).thenReturn(emptyList())

        val response = adminController.getPendingDoctors()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }

    // --- getAllDoctors ---

    @Test
    fun `getAllDoctors returns 200 with all doctors regardless of status`() {
        val doctors = listOf(doctorResponse(1L, UserStatus.ACTIVE), doctorResponse(2L, UserStatus.PENDING))
        whenever(userService.getAllDoctors()).thenReturn(doctors)

        val response = adminController.getAllDoctors()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(2)
    }

    @Test
    fun `getAllDoctors returns 200 with empty list when no doctors registered`() {
        whenever(userService.getAllDoctors()).thenReturn(emptyList())

        val response = adminController.getAllDoctors()

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }

    // --- approveDoctor ---

    @Test
    fun `approveDoctor returns 200 with the approved doctor`() {
        val approved = doctorResponse(1L, UserStatus.ACTIVE)
        whenever(userService.approveDoctor(1L)).thenReturn(approved)

        val response = adminController.approveDoctor(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.id).isEqualTo(1L)
        assertThat(response.body?.status).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    fun `approveDoctor returns 404 when doctor is not found`() {
        whenever(userService.approveDoctor(99L)).thenReturn(null)

        val response = adminController.approveDoctor(99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `approveDoctor returns 409 when doctor is not in pending state`() {
        whenever(userService.approveDoctor(1L)).thenThrow(IllegalStateException("Only pending doctors can be approved"))

        val response = adminController.approveDoctor(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    // --- rejectDoctor ---

    @Test
    fun `rejectDoctor returns 204 when doctor is successfully rejected`() {
        whenever(userService.rejectDoctor(1L)).thenReturn(true)

        val response = adminController.rejectDoctor(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
    }

    @Test
    fun `rejectDoctor returns 404 when doctor is not found`() {
        whenever(userService.rejectDoctor(99L)).thenReturn(false)

        val response = adminController.rejectDoctor(99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `rejectDoctor returns 409 when doctor is not in pending state`() {
        whenever(userService.rejectDoctor(1L)).thenThrow(IllegalStateException("Only pending doctors can be rejected"))

        val response = adminController.rejectDoctor(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    // --- deleteUser ---

    @Test
    fun `deleteUser returns 204 when user is successfully deleted`() {
        setAuthentication("admin@clinic.com")
        whenever(userService.deleteUser(5L, "admin@clinic.com")).thenReturn(true)

        val response = adminController.deleteUser(5L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
    }

    @Test
    fun `deleteUser returns 404 when user to delete is not found`() {
        setAuthentication("admin@clinic.com")
        whenever(userService.deleteUser(99L, "admin@clinic.com")).thenReturn(false)

        val response = adminController.deleteUser(99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `deleteUser returns 409 when deleting own account`() {
        setAuthentication("admin@clinic.com")
        whenever(userService.deleteUser(1L, "admin@clinic.com")).thenThrow(IllegalStateException("Cannot delete your own account"))

        val response = adminController.deleteUser(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `deleteUser returns 409 when deleting the last active admin`() {
        setAuthentication("admin@clinic.com")
        whenever(userService.deleteUser(1L, "admin@clinic.com")).thenThrow(IllegalStateException("Cannot delete the last active admin"))

        val response = adminController.deleteUser(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `deleteUser returns 401 when there is no authenticated principal`() {
        val response = adminController.deleteUser(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun doctorResponse(
        id: Long,
        status: UserStatus,
    ) = DoctorResponse(
        id = id,
        email = "doctor$id@clinic.com",
        name = "Doc",
        surname = "Tor",
        status = status,
    )
}
