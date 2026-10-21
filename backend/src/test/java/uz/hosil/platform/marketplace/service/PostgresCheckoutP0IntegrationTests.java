package uz.hosil.platform.marketplace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uz.hosil.platform.common.error.BusinessConflictException;
import uz.hosil.platform.farm.domain.CropCycleEntity;
import uz.hosil.platform.farm.domain.FarmEntity;
import uz.hosil.platform.farm.domain.FieldEntity;
import uz.hosil.platform.farm.domain.HarvestBatchEntity;
import uz.hosil.platform.farm.repository.CropCycleRepository;
import uz.hosil.platform.farm.repository.FarmRepository;
import uz.hosil.platform.farm.repository.FieldRepository;
import uz.hosil.platform.farm.repository.HarvestBatchRepository;
import uz.hosil.platform.identity.domain.SellerProfileEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.domain.UserProfileEntity;
import uz.hosil.platform.identity.repository.SellerProfileRepository;
import uz.hosil.platform.identity.repository.UserProfileRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.marketplace.domain.CatalogProductEntity;
import uz.hosil.platform.marketplace.domain.CategoryEntity;
import uz.hosil.platform.marketplace.domain.HarvestListingEntity;
import uz.hosil.platform.marketplace.domain.InquiryEntity;
import uz.hosil.platform.marketplace.domain.InquiryOfferEntity;
import uz.hosil.platform.marketplace.domain.InventoryReservationEntity;
import uz.hosil.platform.marketplace.domain.OrderEntity;
import uz.hosil.platform.marketplace.domain.OrderItemEntity;
import uz.hosil.platform.marketplace.domain.SellerOfferEntity;
import uz.hosil.platform.marketplace.repository.CatalogProductRepository;
import uz.hosil.platform.marketplace.repository.CategoryRepository;
import uz.hosil.platform.marketplace.repository.HarvestListingRepository;
import uz.hosil.platform.marketplace.repository.InquiryOfferRepository;
import uz.hosil.platform.marketplace.repository.InquiryRepository;
import uz.hosil.platform.marketplace.repository.InventoryReservationRepository;
import uz.hosil.platform.marketplace.repository.OrderItemRepository;
import uz.hosil.platform.marketplace.repository.OrderRepository;
import uz.hosil.platform.marketplace.repository.SellerOfferRepository;
import uz.hosil.platform.marketplace.web.CreateInputOrderRequest;
import uz.hosil.platform.marketplace.web.CreateWholesaleOrderRequest;
import uz.hosil.platform.marketplace.web.OrderDetailDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 16 + Flyway backed integration tests validating P0 checkout ordering,
 * foreign-key constraints (inventory_reservations -> orders), atomicity, rollback,
 * and concurrent stock reservation.
 */
@SpringBootTest
@ActiveProfiles("local")
class PostgresCheckoutP0IntegrationTests {

    @Autowired
    private WholesaleOrderService wholesaleOrderService;

    @Autowired
    private InputOrderService inputOrderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogProductRepository catalogProductRepository;

    @Autowired
    private SellerOfferRepository sellerOfferRepository;

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
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryOfferRepository inquiryOfferRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    private UserEntity createSyntheticUser(String phonePrefix) {
        String uniqueDigits = String.format("%04d", (int) (Math.random() * 9000) + 1000);
        String phone = phonePrefix + uniqueDigits;
        UserEntity user = new UserEntity(phone, "ACTIVE", "uz");
        user = userRepository.save(user);

        UserProfileEntity profile = new UserProfileEntity(user.getId(), "Test User " + uniqueDigits, "Toshkent", "Yunusobod");
        userProfileRepository.save(profile);
        return user;
    }

    private CategoryEntity getOrCreateCategory(String code, String name) {
        return categoryRepository.findByCode(code).orElseGet(() -> {
            CategoryEntity cat = new CategoryEntity(UUID.randomUUID(), null, code, name, name + " RU", 1);
            return categoryRepository.save(cat);
        });
    }

    @Test
    @DisplayName("P0-1: Wholesale RFQ checkout persists Order before InventoryReservation satisfying PostgreSQL FK")
    void testWholesaleRfqCheckoutAndReservationFk() {
        // 1. Setup seller, buyer, and farm harvest batch
        UserEntity seller = createSyntheticUser("+9989111");
        UserEntity buyer = createSyntheticUser("+9989122");
        CategoryEntity cat = getOrCreateCategory("HARVEST", "Hosil");

        FarmEntity farm = farmRepository.save(
                new FarmEntity(seller.getId(), "Postgres Test Farm", "Samarqand viloyati", "Jomboy", new BigDecimal("40.00"), "FERMER")
        );

        FieldEntity field = fieldRepository.save(
                new FieldEntity(UUID.randomUUID(), farm.getId(), "Field 1", new BigDecimal("10.00"), "HECTARE", null, "Olma bogʻi")
        );

        CropCycleEntity crop = cropCycleRepository.save(
                new CropCycleEntity(UUID.randomUUID(), farm.getId(), field.getId(), "Olma", "Golden", LocalDate.now().minusMonths(6), LocalDate.now(), "HARVESTED")
        );

        HarvestBatchEntity batch = harvestBatchRepository.save(new HarvestBatchEntity(
                UUID.randomUUID(),
                farm.getId(),
                crop.getId(),
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("20000.000"),
                new BigDecimal("15000.000"),
                "KG",
                LocalDate.now(),
                "Ombor #1",
                "RECORDED",
                "BATCH-" + UUID.randomUUID().toString().substring(0, 6),
                null,
                1L
        ));

        HarvestListingEntity listing = listingRepository.save(new HarvestListingEntity(
                UUID.randomUUID(),
                seller.getId(),
                batch.getId(),
                cat.getId(),
                "Eksportbop Golden Olma",
                "Saralangan sifatli olma",
                "Olma (Golden)",
                "FIRST_CLASS",
                new BigDecimal("12000.00"),
                "UZS",
                "RFQ",
                true,
                new BigDecimal("5000.000"),
                new BigDecimal("5000.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "KG",
                new BigDecimal("500.000"),
                new BigDecimal("50.000"),
                "Samarqand viloyati",
                "Jomboy tumani",
                "PUBLISHED",
                null,
                Instant.now().minusSeconds(1800)
        ));

        // 2. Setup RFQ Inquiry and Offer
        UUID agreedOfferId = UUID.randomUUID();
        InquiryEntity inquiry = new InquiryEntity(
                UUID.randomUUID(),
                listing.getId(),
                buyer.getId(),
                seller.getId(),
                "Olma (Golden)",
                "KG",
                "inq-" + UUID.randomUUID().toString().substring(0, 8)
        );
        inquiry = inquiryRepository.save(inquiry);

        InquiryOfferEntity offer = inquiryOfferRepository.save(new InquiryOfferEntity(
                agreedOfferId,
                inquiry.getId(),
                1,
                buyer.getId(),
                "BUYER",
                new BigDecimal("1500.000"),
                "KG",
                new BigDecimal("11200.00"),
                "UZS",
                "FCA (Samarqand)",
                "1500 kg olamiz",
                Instant.now().plusSeconds(86400),
                "offer-" + UUID.randomUUID().toString().substring(0, 8)
        ));

        inquiry.setStatus("AGREED");
        inquiry.setCurrentOfferId(offer.getId());
        inquiry.setAgreedOfferId(offer.getId());
        inquiry.setAgreedAt(Instant.now().minusSeconds(300));
        inquiry.setAgreedQuantity(new BigDecimal("1500.000"));
        inquiry.setAgreedUnitPrice(new BigDecimal("11200.00"));
        inquiry.setAgreedCurrency("UZS");
        inquiry.setAgreedDeliveryTerms("FCA (Samarqand)");
        inquiry = inquiryRepository.save(inquiry);

        // 3. Convert accepted RFQ to Wholesale Order
        CreateWholesaleOrderRequest req = new CreateWholesaleOrderRequest(
                inquiry.getId(), "HOSIL_LOGISTIKA", "Toshkent ombori", "Urgent export"
        );

        OrderDetailDto orderDetail = wholesaleOrderService.createOrderFromRfq(req, null, buyer.getId()).orderDetail();
        assertNotNull(orderDetail);
        assertNotNull(orderDetail.id());
        assertEquals("AWAITING_SELLER_CONFIRMATION", orderDetail.status());

        // 4. Verify PostgreSQL persistence & Foreign Key integrity
        OrderEntity persistedOrder = orderRepository.findById(orderDetail.id()).orElse(null);
        assertNotNull(persistedOrder, "Master order must exist in PostgreSQL");
        assertEquals(orderDetail.id(), persistedOrder.getId());
        assertEquals("HOSIL_LOGISTIKA", persistedOrder.getDeliveryType());

        Optional<InventoryReservationEntity> resOpt = reservationRepository.findByOrderId(orderDetail.id());
        assertTrue(resOpt.isPresent(), "Inventory reservation must be linked to order in PostgreSQL");
        InventoryReservationEntity reservation = resOpt.get();
        assertEquals(persistedOrder.getId(), reservation.getOrderId(), "Reservation order_id must strictly match order.id");
        assertEquals("ACTIVE", reservation.getStatus());
        assertEquals(new BigDecimal("1500.000"), reservation.getReservedQuantity());

        // 5. Seller confirms order
        OrderDetailDto confirmed = wholesaleOrderService.confirmOrder(orderDetail.id(), seller.getId());
        assertNotNull(confirmed);
        assertEquals("CONFIRMED", confirmed.status());
    }

    @Test
    @DisplayName("P0-2: Agricultural-input direct checkout creates Order, Items, and Reservation with correct FK")
    void testAgriculturalInputCheckoutAndReservation() {
        // 1. Setup supplier, buyer, catalog product, and seller offer
        UserEntity supplier = createSyntheticUser("+9989133");
        UserEntity buyer = createSyntheticUser("+9989144");
        CategoryEntity cat = getOrCreateCategory("INPUTS", "Resurslar");

        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), supplier.getId(), "COMMERCIAL_SUPPLIER", "Chirchiq Kimyo MCHJ", "123456789", "VERIFIED", Instant.now()
        ));

        CatalogProductEntity product = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), cat.getId(), "Mineral UREA 46%", "Карбамид", "FertiBrand", "NPK 46-0-0", "BAG_50KG", "Konsentrlangan mineral oʻgʻit", true
        ));

        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplier.getId(), product.getId(),
                new BigDecimal("250000.00"), "UZS", "BAG_50KG",
                new BigDecimal("50.000"), new BigDecimal("0.000"),
                new BigDecimal("5.000"), new BigDecimal("1.000"),
                "Toshkent viloyati", "Chirchiq",
                "Omborda yetarli miqdorda mavjud", "ACTIVE"
        ));

        // 2. Buyer places agricultural input order
        CreateInputOrderRequest.InputOrderItemRequest itemReq = new CreateInputOrderRequest.InputOrderItemRequest(
                offer.getId(), new BigDecimal("15.000"), "BAG_50KG"
        );
        CreateInputOrderRequest orderReq = new CreateInputOrderRequest(
                List.of(itemReq), "BUYER_ARRANGES", "Chirchiq omboridan olib ketish", "EXW", "Bahorgi ekin"
        );

        InputOrderService.OrderCreationResult result = inputOrderService.createInputOrder(orderReq, null, buyer.getId());
        assertNotNull(result);
        OrderDetailDto detail = result.orderDetail();
        assertNotNull(detail);
        assertEquals("PENDING_PAYMENT", detail.status());

        // 3. Verify PostgreSQL DB state
        OrderEntity order = orderRepository.findById(detail.id()).orElse(null);
        assertNotNull(order, "Order must be persisted in PostgreSQL");
        assertEquals("INPUT_DIRECT_CHECKOUT", order.getOrderFlowType());
        assertEquals("BUYER_ARRANGES", order.getDeliveryType());

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        assertEquals(1, items.size());
        assertEquals(new BigDecimal("15.000"), items.get(0).getQuantity());

        Optional<InventoryReservationEntity> resOpt = reservationRepository.findByOrderId(order.getId());
        assertTrue(resOpt.isPresent());
        InventoryReservationEntity reservation = resOpt.get();
        assertEquals(order.getId(), reservation.getOrderId());
        assertEquals(new BigDecimal("15.000"), reservation.getReservedQuantity());

        // 4. Verify seller offer stock decremented
        SellerOfferEntity updatedOffer = sellerOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals(new BigDecimal("35.000"), updatedOffer.getAvailableQuantity(), "Stock must decrease from 50 to 35");
    }

    @Test
    @DisplayName("P0-3: Failed checkout rolls back completely leaving 0 orphaned rows and unchanged stock")
    void testCheckoutRollbackOnFailureLeavesZeroOrphans() {
        // 1. Setup supplier, buyer, offer with stock = 10
        UserEntity supplier = createSyntheticUser("+9989155");
        UserEntity buyer = createSyntheticUser("+9989166");
        CategoryEntity cat = getOrCreateCategory("INPUTS", "Resurslar");

        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), supplier.getId(), "COMMERCIAL_SUPPLIER", "Seed Supplier MCHJ", "987654321", "VERIFIED", Instant.now()
        ));

        CatalogProductEntity product = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), cat.getId(), "Wheat Seeds Super", "Bug'doy", "SeedCorp", "Grade 1", "BAG_50KG", "Urug'lik bug'doy", true
        ));

        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplier.getId(), product.getId(),
                new BigDecimal("180000.00"), "UZS", "BAG_50KG",
                new BigDecimal("10.000"), new BigDecimal("0.000"),
                new BigDecimal("5.000"), new BigDecimal("1.000"),
                "Samarqand viloyati", "Pastdarg'om",
                "Omborda", "ACTIVE"
        ));

        long orderCountBefore = orderRepository.count();
        long resCountBefore = reservationRepository.count();

        // 2. Request 25 units (exceeds available 10 units) -> triggers BusinessConflictException
        CreateInputOrderRequest.InputOrderItemRequest itemReq = new CreateInputOrderRequest.InputOrderItemRequest(
                offer.getId(), new BigDecimal("25.000"), "BAG_50KG"
        );
        CreateInputOrderRequest orderReq = new CreateInputOrderRequest(
                List.of(itemReq), "BUYER_ARRANGES", "Pastdarg'om", "EXW", "Test Rollback"
        );

        assertThrows(BusinessConflictException.class, () -> {
            inputOrderService.createInputOrder(orderReq, null, buyer.getId());
        });

        // 3. Verify PostgreSQL rollback: counts unchanged, stock untouched
        assertEquals(orderCountBefore, orderRepository.count(), "No order should be created");
        assertEquals(resCountBefore, reservationRepository.count(), "No reservation should be created");

        SellerOfferEntity offerAfter = sellerOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals(new BigDecimal("10.000"), offerAfter.getAvailableQuantity(), "Stock must remain 10.000");
    }

    @Test
    @DisplayName("P0-4: Concurrent purchase race prevents overselling and guarantees exact stock invariant")
    void testConcurrentLimitedStockPurchaseRace() throws Exception {
        // 1. Setup supplier, 2 competing buyers, offer with stock = 15
        UserEntity supplier = createSyntheticUser("+9989177");
        UserEntity buyer1 = createSyntheticUser("+9989188");
        UserEntity buyer2 = createSyntheticUser("+9989199");
        CategoryEntity cat = getOrCreateCategory("INPUTS", "Resurslar");

        sellerProfileRepository.save(new SellerProfileEntity(
                UUID.randomUUID(), supplier.getId(), "COMMERCIAL_SUPPLIER", "Race Supplier MCHJ", "555444333", "VERIFIED", Instant.now()
        ));

        CatalogProductEntity product = catalogProductRepository.save(new CatalogProductEntity(
                UUID.randomUUID(), cat.getId(), "Cotton Defoliant Alpha", "Дефолиант", "AgroChem", "99% active", "BAG_50KG", "Kimyoviy modda", true
        ));

        SellerOfferEntity offer = sellerOfferRepository.save(new SellerOfferEntity(
                UUID.randomUUID(), supplier.getId(), product.getId(),
                new BigDecimal("320000.00"), "UZS", "BAG_50KG",
                new BigDecimal("15.000"), new BigDecimal("0.000"),
                new BigDecimal("5.000"), new BigDecimal("1.000"),
                "Namangan", "Pop",
                "Pop warehouse", "ACTIVE"
        ));

        // 2. Launch 2 threads concurrently requesting 10.000 each (10 + 10 = 20 > 15)
        CreateInputOrderRequest.InputOrderItemRequest item = new CreateInputOrderRequest.InputOrderItemRequest(
                offer.getId(), new BigDecimal("10.000"), "BAG_50KG"
        );
        CreateInputOrderRequest req = new CreateInputOrderRequest(
                List.of(item), "BUYER_ARRANGES", "Pop", "EXW", "Race Test"
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Boolean> task1 = () -> {
            try {
                inputOrderService.createInputOrder(req, null, buyer1.getId());
                successCount.incrementAndGet();
                return true;
            } catch (BusinessConflictException e) {
                conflictCount.incrementAndGet();
                return false;
            }
        };

        Callable<Boolean> task2 = () -> {
            try {
                inputOrderService.createInputOrder(req, null, buyer2.getId());
                successCount.incrementAndGet();
                return true;
            } catch (BusinessConflictException e) {
                conflictCount.incrementAndGet();
                return false;
            }
        };

        List<Future<Boolean>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        // 3. Exactly 1 winner, exactly 1 conflict
        assertEquals(1, successCount.get(), "Exactly 1 concurrent purchase must succeed");
        assertEquals(1, conflictCount.get(), "Exactly 1 concurrent purchase must be rejected due to stock conflict");

        // 4. Exact remaining stock check: 15.000 - 10.000 = 5.000
        SellerOfferEntity finalOffer = sellerOfferRepository.findById(offer.getId()).orElseThrow();
        assertEquals(new BigDecimal("5.000"), finalOffer.getAvailableQuantity(), "Remaining stock must be exactly 5.000");
    }
}
