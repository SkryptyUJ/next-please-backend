### 1. Backend

| Criterion                | Kotlin with Spring Boot | Node.js + NestJS                                  |
|:-------------------------|:------------------------|:--------------------------------------------------|
| **Type safety**          | High                    | Medium (even with TS)                             |
| **Development speed**    | High                    | High                                              |
| **Resource consumption** | Medium (JVM)            | Low                                               |
| **Multithreading**       | High (Coroutines)       | Medium (good asynchonous support, but one thread) |

**Current choice:** Kotlin with Spring Boot - great support for authorization using Spring Support, 
and database integration with Spring Data. The resource consumption is negligible for our use case.

---

### 2. Database

| Criterion                 | PostgreSQL     | MongoDB | Redis     |
|:--------------------------|:---------------|:--------|:----------|
| **Data consistency**      | Full           | Medium  | None      |
| **Relationship support**  | High           | Low     | Low       |
| **Read/write efficiency** | Medium         | High    | Very high |
| **Scheme flexibility**    | Medium (JSONB) | High    | Low       |

**Current choice:** PostgreSQL - the relationships between tickets, patients, and rooms are important in our application.
PostgreSQL's performance is sufficient for the scale of handling clinic.


### 4. Real-time Communication

| Criterion                   | Server-Sent Events             | WebSockets        | Polling                    |
|:----------------------------|:-------------------------------|:------------------|:---------------------------|
| **Server overhead**         | Low (one open HTTP connection) | Low               | Very high                  |
| **Communication direction** | Server -> Client               | Client <-> Server | Client -> Server -> Client |
| **Implementation ease**     | Very high                      | Medium            | High                       |
| **Connection resilience**   | High                           | Medium            | High                       |

**Current choice:** Server-Sent Events (SSE) - the waiting room screens and patients' phones only need to receive
notifications about new ticket statuses.