# System Design

## Goal

Let many users compete for the same seats while guaranteeing that a seat is never sold twice, and that a retried request never creates a second booking.

## Components

```mermaid
flowchart LR
    C[Client] -->|JWT| API[Spring Boot API]
    API -->|seat holds with TTL| R[(Redis)]
    API -->|bookings, seats, users| P[(PostgreSQL)]
    API -->|after commit| K[[Kafka: booking-confirmed]]
    K --> N[Notification consumer]
    N --> E[Email sender, mock]
    N -->|after 3 retries| D[[Dead-letter topic]]
```

## Booking flow

```mermaid
sequenceDiagram
    actor U as User
    participant A as API
    participant R as Redis
    participant P as PostgreSQL
    participant K as Kafka
    U->>A: POST /api/shows/{id}/hold
    A->>P: Are the seats AVAILABLE?
    A->>R: Lua script, hold all seats or none
    alt a seat is held by another user
        A-->>U: 409 Conflict
    else all seats held
        A-->>U: 200, expires in 300 s
    end
    U->>A: POST /api/bookings with Idempotency-Key
    A->>P: Key already used by this user?
    A->>R: Does this user still own the holds?
    A->>P: Insert booking, mark seats BOOKED (version check)
    P-->>A: Commit
    A->>R: Release own holds
    A->>K: Publish booking-confirmed (async)
    A-->>U: 200 CONFIRMED
```

## Data model

```mermaid
erDiagram
    SHOWS ||--o{ SEATS : has
    SHOWS ||--o{ BOOKINGS : has
    BOOKINGS ||--|{ BOOKING_SEATS : contains
    SEATS ||--o{ BOOKING_SEATS : "booked as"
    BOOKINGS ||--o| NOTIFICATIONS : triggers
    SEATS {
        bigint id PK
        bigint show_id
        string seat_number
        string status
        bigint version
    }
    BOOKINGS {
        bigint id PK
        bigint show_id
        string user_email
        string idempotency_key
    }
    NOTIFICATIONS {
        bigint id PK
        bigint booking_id UK
    }
```

Relations are stored as ids. `(user_email, idempotency_key)` and `(show_id, seat_number)` are unique.

## Failure handling

| Failure | What happens |
|---|---|
| User abandons checkout | The hold expires after 5 minutes |
| Hold expires before confirming | Confirm returns 409 and the seat stays available |
| Two confirms reach the database together | `@Version` lets one commit, the other gets 409 |
| Client retries a confirm | The same key returns the same booking |
| Key reused for different seats | 422 |
| Email fails | Retried 3 times, then dead-lettered. The booking stands |
| Message delivered twice | The consumer skips bookings already notified |

## Trade-offs

- **Optimistic over pessimistic locking:** Redis removes most contention, so database conflicts are rare and no row locks are held while a user decides.
- **Publish after commit, without an outbox:** simpler, but a crash between commit and publish loses that notification. A transactional outbox would fix it.
- **Single Redis node:** losing it means users must hold their seats again. It cannot cause double booking, because PostgreSQL has the final say.
