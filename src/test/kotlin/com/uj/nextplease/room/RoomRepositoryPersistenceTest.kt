package com.uj.nextplease.room

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.user.User
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class RoomRepositoryPersistenceTest(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRepository: UserRepository,
) {
    @BeforeEach
    fun cleanDatabase() {
        roomRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    @Test
    fun `save room and find by doctor id`() {
        val doctor =
            userRepository.save(
                User(
                    email = "room-doctor@example.com",
                    role = "ROLE_DOCTOR",
                    name = "Anna",
                    surname = "Smith",
                ),
            )

        val saved =
            roomRepository.save(
                Room(
                    name = "Room A",
                    isActive = true,
                    doctorId = doctor.id,
                ),
            )

        val found = roomRepository.findByDoctorId(doctor.id!!)

        assertThat(saved.id).isNotNull()
        assertThat(found).isNotNull()
        assertThat(found?.id).isEqualTo(saved.id)
    }

    @Test
    fun `find by name returns room`() {
        roomRepository.save(Room(name = "Room B", isActive = true))

        val found = roomRepository.findByName("Room B")

        assertThat(found).isNotNull()
        assertThat(found?.name).isEqualTo("Room B")
    }

    @Test
    fun `findAllActive returns only active rooms ordered by name`() {
        roomRepository.save(Room(name = "Room Z", isActive = false))
        roomRepository.save(Room(name = "Room A", isActive = true))
        roomRepository.save(Room(name = "Room C", isActive = true))

        val activeRooms = roomRepository.findAllActive()

        assertThat(activeRooms).extracting("name").containsExactly("Room A", "Room C")
    }
}
