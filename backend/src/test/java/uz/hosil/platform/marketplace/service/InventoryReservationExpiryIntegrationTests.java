package uz.hosil.platform.marketplace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestAllocationEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.OrderStatusHistoryEntity;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestAllocationRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.OrderStatusHistoryRepository;
import uz.hosil.platform.marketplace.worker.InventoryReservationExpiryWorker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "hosil.inventory.expiry.enabled=true")
@ActiveProfiles("test")
class InventoryReservationExpiryIntegrationTests {

    @Autowired
    private InventoryReservationExpiryService expiryService;

    @Autowired
    private InventoryReservationExpiryWorker expiryWorker;

    @Autowired
    private WholesaleOrderService wholesaleOrderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private CropCycleRepository cropCycleRepository;

    @Autowired
    private HarvestBatchRepository harvestBatchRepository;

    @Autowired
    private HarvestListingRepository listingRepository;

    @Autowired
    private HarvestAllocationRepository allocationRepository;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryOfferRepository inquiryOfferRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UserEntity sellerUser;
    private UserEntity buyerUser;
    private HarvestListingEntity listing;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository.deleteAll();
        orderItemRepository.deleteAll();
        inventoryReservationRepository.deleteAll();
        orderRepository.deleteAll();
        inquiryOfferRepository.deleteAll();
        inquiryRepository.deleteAll();
        allocationRepository.deleteAll();
        listingRepository.deleteAll();
        harvestBatchRepository.deleteAll();
        cropCycleRepository.deleteAll();
        fieldRepository.deleteAll();
        farmRepository.deleteAll();
        userProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Root Category
        CategoryEntity category = categoryRepository.findByCode("HARVEST")
                .orElseGet(() -> categoryRepository.save(new CategoryEntity(
                        UUID.randomUUID(), null, "HARVEST", "Hosil", "Урожай", 1
                )));

        // 2. Users
        sellerUser = userRepository.save(new UserEntity("+998901112233", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(sellerUser.getId(), "Bahrom Dehqon", "Samarqand viloyati", "Jomboy"));

        buyerUser = userRepository.save(new UserEntity("+998904445566", "ACTIVE", "uz"));
        userProfileRepository.save(new UserProfileEntity(buyerUser.getId(), "Nodir Xaridor", "Toshkent shahri", "Yunusobod"));

        // 3. Farm, Field, Crop Cycle, Batch
        FarmEntity farm = farmRepository.save(new FarmEntity(sellerUser.getId(), "Samarqand Bogʻlari", "Samarqand viloyati", "Jomboy", new BigDecimal("40.00"), "FERMER"));
        FieldEntity field = fieldRepository.save(new FieldEntity(UUID.randomUUID(), farm.getId(), "Olmazor 3-uchastka", new BigDecimal("10.00"), "HECTARE", null, "Organik olma"));
        CropCycleEntity cropCycle = cropCycleRepository.save(new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Olma", "Golden", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1), "HARVESTED"));

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                cropCycle.getId(),
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("20000.000"),
                new BigDecimal("10000.000"),
                "KG",
                LocalDate.of(2026, 9, 10),
                "Ombor",
                "PARTIALLY_ALLOCATED",
                "batch-exp-test",
                null,
                1L
        ));

        // 4. Listing: allocated = 10000, available = 8000, reserved = 2000, sold = 0
        listing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                sellerUser.getId(),
                batch.getId(),
                category.getId(),
                "Olma Golden wholesale",
                "Eksportbop saralangan olma",
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("8000.00"),
                "UZS",
                "RFQ",
                true,
                new BigDecimal("10000.000"),
                new BigDecimal("8000.000"),
                new BigDecimal("2000.000"),
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("1000.000"),
                new BigDecimal("500.000"),
                "Samarqand viloyati",
                "Jomboy",
                "PUBLISHED",
                null,
                Instant.now()
        ));

        allocationRepository.save(new HarvestAllocationEntity(
                UUID.randomUUID(),
                batch.getId(),
                listing.getId(),
                new BigDecimal("10000.000"),
                "KG",
                "ACTIVE",
                Instant.now()
        ));
    }

    private OrderAndReservation createTestOrderScenario(String orderStatus, String reservationStatus, Instant reservationExpiresAt, BigDecimal reservedQty) {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity(
                orderId,
                "ORD-TEST-" + UUID.randomUUID().toString().substring(0, 8),
                buyerUser.getId(),
                sellerUser.getId(),
                null,
                null,
                listing.getId(),
                "HARVEST_PURCHASE_REQUEST",
                reservedQty.multiply(new BigDecimal("8000.00")),
                "UZS",
                orderStatus,
                "BUYER_ARRANGES",
                "Toshkent",
                "FCA",
                "Notes",
                reservationExpiresAt,
                null
        );
        orderRepository.save(order);

        InventoryReservationEntity reservation = new InventoryReservationEntity(
                UUID.randomUUID(),
                orderId,
                listing.getId(),
                reservedQty,
                listing.getUnitCode(),
                reservationExpiresAt,
                reservationStatus
        );
        inventoryReservationRepository.save(reservation);

        return new OrderAndReservation(order, reservation);
    }

    private record OrderAndReservation(OrderEntity order, InventoryReservationEntity reservation) {}

    @Test
    @DisplayName("AC-EXP-1: Expired active reservation is safely released and unconfirmed order cancelled")
    void expiredActiveReservation_isReleased_andOrderCancelled() {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(15));
        OrderAndReservation scenario = createTestOrderScenario("AWAITING_SELLER_CONFIRMATION", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        int released = expiryService.sweepExpiredReservations(10);
        assertEquals(1, released, "Expected exactly 1 expired reservation to be released");

        // Verify reservation
        InventoryReservationEntity updatedRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertEquals("RELEASED_EXPIRED", updatedRes.getStatus());

        // Verify listing quantities restored
        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.000"), updatedListing.getReservedQuantity());
        assertEquals(new BigDecimal("10000.000"), updatedListing.getAvailableQuantity());

        // Verify quantity conservation: allocated = available + reserved + sold
        assertEquals(updatedListing.getAllocatedQuantity(),
                updatedListing.getAvailableQuantity().add(updatedListing.getReservedQuantity()).add(updatedListing.getSoldQuantity()));

        // Verify order transitioned to CANCELLED
        OrderEntity updatedOrder = orderRepository.findById(scenario.order.getId()).orElseThrow();
        assertEquals("CANCELLED", updatedOrder.getStatus());
        assertNotNull(updatedOrder.getCancelledAt());
        assertTrue(updatedOrder.getCancellationReason().contains("Tasdiqlash muddati"));

        // Verify order status history
        List<OrderStatusHistoryEntity> history = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(scenario.order.getId());
        assertFalse(history.isEmpty());
        OrderStatusHistoryEntity lastHistory = history.get(history.size() - 1);
        assertEquals("AWAITING_SELLER_CONFIRMATION", lastHistory.getFromStatus());
        assertEquals("CANCELLED", lastHistory.getToStatus());
        assertEquals("SYSTEM", lastHistory.getActorRole());
    }

    @Test
    @DisplayName("AC-EXP-2: Unexpired active reservation remains untouched")
    void unexpiredActiveReservation_remainsActive_andNotReleased() {
        Instant futureExpiry = Instant.now().plus(Duration.ofHours(24));
        OrderAndReservation scenario = createTestOrderScenario("AWAITING_SELLER_CONFIRMATION", "ACTIVE", futureExpiry, new BigDecimal("2000.000"));

        int released = expiryService.sweepExpiredReservations(10);
        assertEquals(0, released, "Expected 0 reservations released for unexpired active reservation");

        InventoryReservationEntity updatedRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertEquals("ACTIVE", updatedRes.getStatus());

        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("2000.000"), updatedListing.getReservedQuantity());
        assertEquals(new BigDecimal("8000.000"), updatedListing.getAvailableQuantity());

        OrderEntity updatedOrder = orderRepository.findById(scenario.order.getId()).orElseThrow();
        assertEquals("AWAITING_SELLER_CONFIRMATION", updatedOrder.getStatus());
    }

    @Test
    @DisplayName("AC-EXP-3: Already released reservation is never processed or credited twice")
    void alreadyReleasedReservation_isNotProcessedTwice() {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(15));
        OrderAndReservation scenario = createTestOrderScenario("CANCELLED", "RELEASED_EXPIRED", pastExpiry, new BigDecimal("2000.000"));

        int released = expiryService.sweepExpiredReservations(10);
        assertEquals(0, released, "Expected 0 reservations processed for already released reservation");

        // Verify stock is not altered
        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("2000.000"), updatedListing.getReservedQuantity());
        assertEquals(new BigDecimal("8000.000"), updatedListing.getAvailableQuantity());
    }

    @Test
    @DisplayName("AC-EXP-4: Confirmed-sold reservation is never released")
    void confirmedSoldReservation_isNotReleased() {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(15));
        OrderAndReservation scenario = createTestOrderScenario("COMPLETED", "CONFIRMED_SOLD", pastExpiry, new BigDecimal("2000.000"));

        int released = expiryService.sweepExpiredReservations(10);
        assertEquals(0, released, "Expected 0 reservations processed for confirmed sold reservation");

        InventoryReservationEntity updatedRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertEquals("CONFIRMED_SOLD", updatedRes.getStatus());

        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("2000.000"), updatedListing.getReservedQuantity());
        assertEquals(new BigDecimal("8000.000"), updatedListing.getAvailableQuantity());
    }

    @Test
    @DisplayName("AC-EXP-5: Confirmed order is not cancelled or released by confirmation expiry worker")
    void confirmedOrder_isNotCancelledOrReleasedByConfirmationTimeout() {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(15));
        OrderAndReservation scenario = createTestOrderScenario("CONFIRMED", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        int released = expiryService.sweepExpiredReservations(10);
        assertEquals(0, released, "Confirmed order must not be released or cancelled by confirmation timeout worker");

        OrderEntity updatedOrder = orderRepository.findById(scenario.order.getId()).orElseThrow();
        assertEquals("CONFIRMED", updatedOrder.getStatus());

        InventoryReservationEntity updatedRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertEquals("ACTIVE", updatedRes.getStatus());

        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("2000.000"), updatedListing.getReservedQuantity());
        assertEquals(new BigDecimal("8000.000"), updatedListing.getAvailableQuantity());
    }

    @Test
    @DisplayName("AC-EXP-6: PENDING_PAYMENT order transitions to CANCELLED on payment timeout")
    void pendingPaymentOrder_transitionsToCancelledOnExpiry() {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(15));
        OrderAndReservation scenario = createTestOrderScenario("PENDING_PAYMENT", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        int released = expiryService.sweepExpiredReservations(10);
        assertEquals(1, released, "Pending payment order should be expired and cancelled");

        OrderEntity updatedOrder = orderRepository.findById(scenario.order.getId()).orElseThrow();
        assertEquals("CANCELLED", updatedOrder.getStatus());
        assertTrue(updatedOrder.getCancellationReason().contains("Toʻlov muddati"));

        InventoryReservationEntity updatedRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertEquals("RELEASED_EXPIRED", updatedRes.getStatus());

        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.000"), updatedListing.getReservedQuantity());
        assertEquals(new BigDecimal("10000.000"), updatedListing.getAvailableQuantity());
    }

    @Test
    @DisplayName("AC-EXP-7: Inventory quantity conservation invariant holds before and after expiry")
    void inventoryQuantityConservation_holdsStrictly() {
        HarvestListingEntity initialListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(0, initialListing.getAllocatedQuantity().compareTo(new BigDecimal("10000.000")));
        assertEquals(0, initialListing.getAvailableQuantity().compareTo(new BigDecimal("8000.000")));
        assertEquals(0, initialListing.getReservedQuantity().compareTo(new BigDecimal("2000.000")));
        assertEquals(0, initialListing.getSoldQuantity().compareTo(BigDecimal.ZERO));

        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(10));
        createTestOrderScenario("AWAITING_SELLER_CONFIRMATION", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        expiryService.sweepExpiredReservations(10);

        HarvestListingEntity finalListing = listingRepository.findById(listing.getId()).orElseThrow();
        BigDecimal total = finalListing.getAvailableQuantity()
                .add(finalListing.getReservedQuantity())
                .add(finalListing.getSoldQuantity());

        assertEquals(0, finalListing.getAllocatedQuantity().compareTo(total), "Allocated must equal available + reserved + sold");
        assertEquals(0, finalListing.getAvailableQuantity().compareTo(new BigDecimal("10000.000")));
        assertEquals(0, finalListing.getReservedQuantity().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("AC-EXP-8: Concurrent worker attempts process an expired reservation exactly once")
    void concurrentWorkerAttempts_processReservationExactlyOnce() throws Exception {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(10));
        OrderAndReservation scenario = createTestOrderScenario("AWAITING_SELLER_CONFIRMATION", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean result = expiryService.processSingleExpiredReservation(scenario.reservation.getId(), Instant.now());
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        skipCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore concurrency conflicts handled gracefully
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one worker thread must succeed in releasing the reservation");
        assertEquals(threadCount - 1, skipCount.get(), "Other worker threads must safely skip");

        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("10000.000"), updatedListing.getAvailableQuantity(), "Available quantity must be restored exactly once");
        assertEquals(new BigDecimal("0.000"), updatedListing.getReservedQuantity(), "Reserved quantity must be zero");
    }

    @Test
    @DisplayName("AC-EXP-9: Transaction rollback restores consistent state when failure occurs")
    void transactionRollback_restoresConsistentState_onSimulatedFailure() {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(10));
        OrderAndReservation scenario = createTestOrderScenario("AWAITING_SELLER_CONFIRMATION", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> {
            txTemplate.execute(status -> {
                // Manually modify listing
                HarvestListingEntity l = listingRepository.findByIdForUpdate(listing.getId()).orElseThrow();
                l.setAvailableQuantity(l.getAvailableQuantity().add(new BigDecimal("2000.000")));
                l.setReservedQuantity(BigDecimal.ZERO);
                listingRepository.save(l);

                InventoryReservationEntity r = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
                r.setStatus("RELEASED_EXPIRED");
                inventoryReservationRepository.save(r);

                // Simulate catastrophic database / runtime failure before commit
                throw new RuntimeException("Simulated catastrophic failure inside transaction");
            });
        });

        // Verify that rollback occurred completely
        HarvestListingEntity rolledBackListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("8000.000"), rolledBackListing.getAvailableQuantity(), "Available quantity must rollback");
        assertEquals(new BigDecimal("2000.000"), rolledBackListing.getReservedQuantity(), "Reserved quantity must rollback");

        InventoryReservationEntity rolledBackRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertEquals("ACTIVE", rolledBackRes.getStatus(), "Reservation status must rollback to ACTIVE");
    }

    @Test
    @DisplayName("AC-EXP-10: Cancellation and expiry racing against each other complete safely without deadlock")
    void cancellationAndExpiryRacing_bothCompleteSafelyWithoutDeadlock() throws Exception {
        Instant pastExpiry = Instant.now().minus(Duration.ofMinutes(10));
        OrderAndReservation scenario = createTestOrderScenario("AWAITING_SELLER_CONFIRMATION", "ACTIVE", pastExpiry, new BigDecimal("2000.000"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Callable<Boolean> expiryTask = () -> {
            startLatch.await();
            return expiryService.processSingleExpiredReservation(scenario.reservation.getId(), Instant.now());
        };

        Callable<Boolean> cancelTask = () -> {
            startLatch.await();
            try {
                wholesaleOrderService.cancelOrder(scenario.order.getId(), "Buyer cancellation race", buyerUser.getId());
                return true;
            } catch (Exception e) {
                return false;
            }
        };

        Future<Boolean> expiryFuture = executor.submit(expiryTask);
        Future<Boolean> cancelFuture = executor.submit(cancelTask);

        startLatch.countDown();

        Boolean expiryResult = expiryFuture.get(10, TimeUnit.SECONDS);
        Boolean cancelResult = cancelFuture.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should have performed the de-allocation, or both completed gracefully
        HarvestListingEntity updatedListing = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(new BigDecimal("10000.000"), updatedListing.getAvailableQuantity(), "Available quantity must be exactly 10000.000");
        assertEquals(new BigDecimal("0.000"), updatedListing.getReservedQuantity(), "Reserved quantity must be exactly 0.000");

        OrderEntity finalOrder = orderRepository.findById(scenario.order.getId()).orElseThrow();
        assertEquals("CANCELLED", finalOrder.getStatus());

        InventoryReservationEntity finalRes = inventoryReservationRepository.findById(scenario.reservation.getId()).orElseThrow();
        assertTrue(List.of("RELEASED_EXPIRED", "RELEASED_CANCELLED").contains(finalRes.getStatus()));
    }

    @Test
    @DisplayName("AC-EXP-11: InventoryReservationExpiryWorker executes sweep without unhandled error")
    void scheduledWorker_executesSweepWhenEnabled() {
        assertDoesNotThrow(() -> expiryWorker.runExpirySweep());
    }
}
