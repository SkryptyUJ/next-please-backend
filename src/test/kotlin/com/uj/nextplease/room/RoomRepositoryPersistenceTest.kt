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
    fun `given a seated doctor when findByDoctorId then the room is returned`() {
        val doctor =
            userRepository.save(
                User(
                    email = "room-doctor@example.com",
                    password = "encoded",
                    role = "DOCTOR",
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
    fun `given free and occupied rooms when findByDoctorIdIsNull then only free rooms are returned`() {
        val doctor =
            userRepository.save(
                User(
                    email = "occupier@example.com",
                    password = "encoded",
                    role = "DOCTOR",
                    name = "Piotr",
                    surname = "Nowak",
                ),
            )
        roomRepository.save(Room(name = "Free Room", isActive = false, doctorId = null))
        roomRepository.save(Room(name = "Taken Room", isActive = true, doctorId = doctor.id))

        val free = roomRepository.findByDoctorIdIsNull()

        assertThat(free).extracting("name").containsExactly("Free Room")
    }
}
