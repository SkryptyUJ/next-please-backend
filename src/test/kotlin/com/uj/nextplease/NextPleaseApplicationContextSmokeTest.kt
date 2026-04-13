package com.uj.nextplease

import com.uj.nextplease.config.PostgresTestContainerConfig
import com.uj.nextplease.room.repository.RoomRepository
import com.uj.nextplease.ticket.repository.TicketRepository
import com.uj.nextplease.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PostgresTestContainerConfig::class)
class NextPleaseApplicationContextSmokeTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val ticketRepository: TicketRepository,
) {
    @Test
    fun `context loads with repositories`() {
        assertThat(userRepository).isNotNull()
        assertThat(roomRepository).isNotNull()
        assertThat(ticketRepository).isNotNull()
    }
}
