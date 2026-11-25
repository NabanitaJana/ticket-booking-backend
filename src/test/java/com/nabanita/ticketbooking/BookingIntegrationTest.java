package com.nabanita.ticketbooking;

import com.nabanita.ticketbooking.config.AppProperties;
import com.nabanita.ticketbooking.dto.*;
import com.nabanita.ticketbooking.entity.SeatStatus;
import com.nabanita.ticketbooking.exception.ApiException;
import com.nabanita.ticketbooking.notification.EmailSender;
import com.nabanita.ticketbooking.repository.*;
import com.nabanita.ticketbooking.service.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingIntegrationTest {

    static final int THREADS = 100;

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        Startables.deepStart(postgres, kafka, redis).join();
    }

    static final Set<String> deadLettered = ConcurrentHashMap.newKeySet();

    @TestConfiguration
    static class DeadLetterProbe {
        @KafkaListener(topics = "booking-confirmed-dlt", groupId = "dead-letter-probe")
        void onDeadLetter(ConsumerRecord<String, ?> record) {
            deadLettered.add(record.key());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService authService;
    @Autowired ShowService showService;
    @Autowired BookingService bookingService;
    @Autowired SeatRepository seatRepo;
    @Autowired BookingRepository bookingRepo;
    @Autowired NotificationRepository notificationRepo;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired AppProperties props;
    @MockitoSpyBean SeatHoldService holdService;
    @MockitoBean EmailSender emailSender;

    Long showId;
    List<Long> seats;

    @BeforeEach
    void createShow() {
        showId = showService.createShow(
                new CreateShowRequest("Show", "Venue", LocalDateTime.now().plusDays(1), 3)).getId();
        seats = showService.getSeats(showId).stream().map(SeatResponse::id).toList();
    }

    @Test
    void hundredUsersHoldOneSeat_exactlyOneSucceeds() throws Exception {
        var outcomes = race(i -> bookingService.holdSeats(showId, Set.of(seats.get(0)), user(i)));

        assertThat(outcomes).filteredOn(o -> o instanceof HoldResponse).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof ApiException).hasSize(THREADS - 1);
    }

    @Test
    void hundredUsersConfirmOneSeatWithRedisBypassed_databaseAllowsExactlyOne() throws Exception {
        doReturn(true).when(holdService).isHeldBy(any(), any(), anyString());

        var outcomes = race(i -> bookingService.confirm(confirm(seats.get(0)), "key", user(i)));

        assertThat(outcomes).filteredOn(o -> o instanceof BookingResponse).hasSize(1);
        assertThat(bookingRepo.findAll()).filteredOn(b -> b.getShowId().equals(showId)).hasSize(1);
        System.out.println("Redis bypassed, losing outcomes: " + outcomes.stream()
                .filter(o -> o instanceof Exception)
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getClass().getSimpleName(),
                        java.util.stream.Collectors.counting())));
    }

    @Test
    void hundredConcurrentRetriesWithSameKey_createOneBooking() throws Exception {
        String user = user(0);
        bookingService.holdSeats(showId, Set.of(seats.get(0)), user);

        var outcomes = race(i -> bookingService.confirm(confirm(seats.get(0)), "same-key", user));

        assertThat(outcomes).allSatisfy(o -> assertThat(o).isInstanceOf(BookingResponse.class));
        assertThat(outcomes.stream().map(o -> ((BookingResponse) o).id()).distinct()).hasSize(1);
    }

    @Test
    void idempotencyKeyReusedForDifferentSeats_isRejected() {
        String user = user(0);
        bookingService.holdSeats(showId, Set.copyOf(seats), user);
        bookingService.confirm(confirm(seats.get(0)), "k", user);

        assertThatThrownBy(() -> bookingService.confirm(confirm(seats.get(1)), "k", user))
                .hasMessageContaining("already used");
    }

    @Test
    void multiSeatHoldIsAllOrNothing() {
        bookingService.holdSeats(showId, Set.of(seats.get(2)), user(1));

        assertThatThrownBy(() -> bookingService.holdSeats(showId, Set.copyOf(seats), user(2)))
                .isInstanceOf(ApiException.class);
        assertThat(holdService.owners(showId, seats)).containsExactly(null, null, user(1));
    }

    @Test
    void holdExpires_repeatDoesNotExtendIt_andExpiredHoldCannotBeConfirmed() {
        Set<Long> seat = Set.of(seats.get(0));
        String key = "seat-hold:" + showId + ":" + seats.get(0);
        bookingService.holdSeats(showId, seat, user(1));
        assertThat(redisTemplate.getExpire(key)).isBetween(1L, props.holdTtl().toSeconds());

        redisTemplate.expire(key, Duration.ofSeconds(1));
        assertThat(bookingService.holdSeats(showId, seat, user(1)).expiresInSeconds()).isEqualTo(1);

        await(() -> !redisTemplate.hasKey(key));
        assertThatThrownBy(() -> bookingService.confirm(confirm(seats.get(0)), "k", user(1)))
                .hasMessageContaining("Hold expired");
        assertThat(seatRepo.findById(seats.get(0)).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void releaseDoesNotDeleteAnotherUsersHold() {
        bookingService.holdSeats(showId, Set.of(seats.get(0)), user(1));
        holdService.release(showId, List.of(seats.get(0)), user(2));
        assertThat(holdService.isHeldBy(showId, List.of(seats.get(0)), user(1))).isTrue();
    }

    @Test
    void transientEmailFailuresAreRetriedUntilSent() {
        String user = user(0);
        doThrow(new IllegalStateException("smtp down")).doThrow(new IllegalStateException("smtp down"))
                .doNothing().when(emailSender).send(eq(user), any(), any());

        Long bookingId = book(user);

        await(() -> notificationRepo.existsByBookingId(bookingId));
        verify(emailSender, times(3)).send(eq(user), any(), any());
        assertThat(deadLettered).doesNotContain(bookingId.toString());
    }

    @Test
    void permanentEmailFailureIsDeadLettered_andBookingStands() {
        String user = user(0);
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(eq(user), any(), any());

        Long bookingId = book(user);

        await(() -> deadLettered.contains(bookingId.toString()));
        verify(emailSender, times(4)).send(eq(user), any(), any());
        assertThat(notificationRepo.existsByBookingId(bookingId)).isFalse();
        assertThat(bookingRepo.existsById(bookingId)).isTrue();
    }

    @Test
    void httpFlow_authRolesHoldConflictAndIdempotentConfirm() throws Exception {
        String alice = token("alice-" + UUID.randomUUID() + "@test.com");
        String bob = token("bob-" + UUID.randomUUID() + "@test.com");
        String admin = "Bearer " + authService.login(
                new AuthRequest(props.admin().email(), props.admin().password())).accessToken();
        String showBody = """
                {"name":"Concert","venue":"Arena","startTime":"2099-01-01T19:00:00","totalSeats":2}""";
        String seatBody = "{\"seatIds\":[" + seats.get(0) + "]}";
        String confirmBody = "{\"showId\":" + showId + ",\"seatIds\":[" + seats.get(0) + "]}";

        mvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(showBody)
                .header("Authorization", alice)).andExpect(status().isForbidden());
        mvc.perform(post("/api/shows").contentType(MediaType.APPLICATION_JSON).content(showBody)
                .header("Authorization", admin)).andExpect(status().isCreated());

        String holdUrl = "/api/shows/" + showId + "/hold";
        mvc.perform(post(holdUrl).contentType(MediaType.APPLICATION_JSON).content(seatBody))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(holdUrl).contentType(MediaType.APPLICATION_JSON).content(seatBody)
                .header("Authorization", alice)).andExpect(status().isOk());
        mvc.perform(post(holdUrl).contentType(MediaType.APPLICATION_JSON).content(seatBody)
                .header("Authorization", bob)).andExpect(status().isConflict());
        mvc.perform(get("/api/shows/" + showId + "/seats"))
                .andExpect(jsonPath("$[0].status").value("HELD"));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(confirmBody)
                            .header("Authorization", alice).header("Idempotency-Key", "http-key"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        }
        mvc.perform(get("/api/bookings").header("Authorization", alice))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/bookings").header("Authorization", bob))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    /** Runs the action on THREADS threads released together; returns each result or exception. */
    private List<Object> race(IntFunction<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return action.apply(n);
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> f : futures) outcomes.add(f.get(60, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Long book(String user) {
        bookingService.holdSeats(showId, Set.of(seats.get(0)), user);
        return bookingService.confirm(confirm(seats.get(0)), "k", user).id();
    }

    private ConfirmRequest confirm(Long seatId) {
        return new ConfirmRequest(showId, Set.of(seatId));
    }

    private String user(int i) {
        return "user" + i + "-show" + showId + "@test.com";
    }

    private String token(String email) {
        return "Bearer " + authService.register(new AuthRequest(email, "password123")).accessToken();
    }

    private void await(BooleanSupplier condition) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(condition::getAsBoolean);
    }
}
