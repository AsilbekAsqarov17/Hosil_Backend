package uz.hosil.platform.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uz.hosil.platform.identity.domain.TelegramLinkingSessionEntity;
import uz.hosil.platform.identity.domain.TelegramUserLinkEntity;
import uz.hosil.platform.identity.domain.UserEntity;
import uz.hosil.platform.identity.repository.OtpChallengeRepository;
import uz.hosil.platform.identity.repository.TelegramLinkingSessionRepository;
import uz.hosil.platform.identity.repository.TelegramProcessedUpdateRepository;
import uz.hosil.platform.identity.repository.TelegramUserLinkRepository;
import uz.hosil.platform.identity.repository.UserRepository;
import uz.hosil.platform.identity.telegram.TelegramApiClient;
import uz.hosil.platform.identity.telegram.TelegramLinkService;
import uz.hosil.platform.identity.telegram.TelegramModels;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "hosil.otp.delivery.provider=TELEGRAM",
        "hosil.telegram.bot-token=test_dummy_token_12345",
        "hosil.telegram.bot-username=hosil_test_bot",
        "hosil.telegram.polling.enabled=false"
})
class TelegramAuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelegramLinkService telegramLinkService;

    @Autowired
    private TelegramUserLinkRepository userLinkRepository;

    @Autowired
    private TelegramLinkingSessionRepository sessionRepository;

    @Autowired
    private TelegramProcessedUpdateRepository processedUpdateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpChallengeRepository otpChallengeRepository;

    @MockBean
    private TelegramApiClient mockTelegramApiClient;

    @BeforeEach
    void setUp() {
        processedUpdateRepository.deleteAll();
        sessionRepository.deleteAll();
        userLinkRepository.deleteAll();
        otpChallengeRepository.deleteAll();
        userRepository.deleteAll();

        when(mockTelegramApiClient.sendMessage(anyLong(), anyString(), any(), any()))
                .thenReturn(new TelegramModels.TelegramMessage(1001L, null, null, 1700000000L, "ok", null));
    }

    @Test
    @DisplayName("Requesting OTP for unlinked phone returns TELEGRAM_LINK_REQUIRED without creating challenge")
    void requestOtp_UnlinkedPhone_ReturnsTelegramLinkRequired() throws Exception {
        String phone = "+998901112233";

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("TELEGRAM_LINK_REQUIRED"))
                .andExpect(jsonPath("$.message").exists());

        // Verify no challenge was created in database
        assertEquals(0, otpChallengeRepository.count());
    }

    @Test
    @DisplayName("Create linking session generates valid deepLink and opaque token without plaintext in DB")
    void createLinkingSession_ValidPhone_SavesHashedNonceAndReturnsDeepLink() throws Exception {
        String phone = "+998901112233";

        mockMvc.perform(post("/api/v1/auth/telegram/link-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TelegramLinkSessionRequest(phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.deepLink").value(org.hamcrest.Matchers.startsWith("https://t.me/hosil_test_bot?start=")))
                .andExpect(jsonPath("$.botUsername").value("hosil_test_bot"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        List<TelegramLinkingSessionEntity> sessions = sessionRepository.findAll();
        assertEquals(1, sessions.size());
        TelegramLinkingSessionEntity saved = sessions.get(0);
        assertEquals(phone, saved.getPhoneNumber());
        assertFalse(saved.isConsumed());
        // Nonce is stored as a 64-char SHA-256 hex string, NOT raw token
        assertEquals(64, saved.getNonceHash().length());
    }

    @Test
    @DisplayName("Telegram linking flow: /start followed by matching contact successfully links chat")
    void telegramLinking_StartAndMatchingContact_EstablishesLinkAndConsumesSession() throws Exception {
        String phone = "+998901112233";
        var sessionDto = telegramLinkService.createLinkingSession(phone);

        Long chatId = 12345678L;
        Long telegramUserId = 87654321L;

        // Step 1: User opens bot and sends /start <token>
        TelegramModels.TelegramUser fromUser = new TelegramModels.TelegramUser(
                telegramUserId, false, "Alisher", "Navoiy", "alisher_n"
        );
        TelegramModels.TelegramChat chat = new TelegramModels.TelegramChat(
                chatId, "private", null, "alisher_n", "Alisher", "Navoiy"
        );
        TelegramModels.TelegramMessage startMessage = new TelegramModels.TelegramMessage(
                1L, fromUser, chat, 1700000000L, "/start " + sessionDto.token(), null
        );

        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(101L, startMessage));

        // Verify bot sent welcome message with request_contact keyboard
        verify(mockTelegramApiClient).sendMessage(eq(chatId), contains("rasmiy autentifikatsiya botiga xush kelibsiz"), eq("Markdown"), any());

        // Step 2: User presses button and shares contact
        TelegramModels.TelegramContact contact = new TelegramModels.TelegramContact(
                "998901112233", "Alisher", "Navoiy", telegramUserId
        );
        TelegramModels.TelegramMessage contactMessage = new TelegramModels.TelegramMessage(
                2L, fromUser, chat, 1700000005L, null, contact
        );

        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(102L, contactMessage));

        // Verify bot sent confirmation message
        verify(mockTelegramApiClient).sendMessage(eq(chatId), contains("muvaffaqiyatli tasdiqlandi"), eq("Markdown"), any());

        // Verify link is established in database
        assertTrue(telegramLinkService.isPhoneLinked(phone));
        TelegramUserLinkEntity link = userLinkRepository.findByPhoneNumber(phone).orElseThrow();
        assertEquals(chatId, link.getTelegramChatId());
        assertEquals(telegramUserId, link.getTelegramUserId());

        // Verify session was consumed
        TelegramLinkingSessionEntity consumedSession = sessionRepository.findAll().get(0);
        assertTrue(consumedSession.isConsumed());
        assertNotNull(consumedSession.getConsumedAt());
    }

    @Test
    @DisplayName("Contact with wrong sender ID (forwarded contact) is rejected without linking")
    void telegramLinking_ContactFromDifferentUser_Rejected() {
        String phone = "+998901112233";
        telegramLinkService.createLinkingSession(phone);

        Long chatId = 12345678L;
        Long senderId = 87654321L;
        Long thirdPartyUserId = 99999999L; // Different user ID!

        TelegramModels.TelegramUser fromUser = new TelegramModels.TelegramUser(
                senderId, false, "Ali", null, "ali"
        );
        TelegramModels.TelegramChat chat = new TelegramModels.TelegramChat(
                chatId, "private", null, "ali", "Ali", null
        );
        TelegramModels.TelegramContact contact = new TelegramModels.TelegramContact(
                "998901112233", "Vali", null, thirdPartyUserId
        );
        TelegramModels.TelegramMessage message = new TelegramModels.TelegramMessage(
                2L, fromUser, chat, 1700000005L, null, contact
        );

        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(201L, message));

        verify(mockTelegramApiClient).sendMessage(eq(chatId), contains("Xavfsizlik qoidasi"), eq("Markdown"), any());
        assertFalse(telegramLinkService.isPhoneLinked(phone));
    }

    @Test
    @DisplayName("Contact with non-matching phone number is rejected without linking")
    void telegramLinking_NonMatchingPhone_Rejected() {
        String sessionPhone = "+998901112233";
        var sessionDto = telegramLinkService.createLinkingSession(sessionPhone);

        Long chatId = 12345678L;
        Long senderId = 87654321L;

        TelegramModels.TelegramUser fromUser = new TelegramModels.TelegramUser(
                senderId, false, "Ali", null, "ali"
        );
        TelegramModels.TelegramChat chat = new TelegramModels.TelegramChat(
                chatId, "private", null, "ali", "Ali", null
        );

        // Step 1: User sends /start <token>
        TelegramModels.TelegramMessage startMessage = new TelegramModels.TelegramMessage(
                1L, fromUser, chat, 1700000000L, "/start " + sessionDto.token(), null
        );
        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(201L, startMessage));

        // Step 2: User shares a non-matching contact phone: +998909998877
        TelegramModels.TelegramContact contact = new TelegramModels.TelegramContact(
                "998909998877", "Ali", null, senderId
        );
        TelegramModels.TelegramMessage message = new TelegramModels.TelegramMessage(
                2L, fromUser, chat, 1700000005L, null, contact
        );

        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(202L, message));

        verify(mockTelegramApiClient).sendMessage(eq(chatId), contains("mos kelmadi"), eq("Markdown"), any());
        assertFalse(telegramLinkService.isPhoneLinked(sessionPhone));
        assertFalse(telegramLinkService.isPhoneLinked("+998909998877"));
    }

    @Test
    @DisplayName("Updates from non-private chats (groups/channels) are ignored")
    void telegramLinking_NonPrivateChat_Ignored() {
        TelegramModels.TelegramUser fromUser = new TelegramModels.TelegramUser(
                111L, false, "GroupMember", null, null
        );
        TelegramModels.TelegramChat groupChat = new TelegramModels.TelegramChat(
                -999L, "group", "Agro Group", null, null, null
        );
        TelegramModels.TelegramMessage msg = new TelegramModels.TelegramMessage(
                10L, fromUser, groupChat, 1700000000L, "/start", null
        );

        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(301L, msg));

        verify(mockTelegramApiClient, never()).sendMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Replayed update ID is ignored idempotently")
    void telegramLinking_ReplayedUpdate_Ignored() {
        TelegramModels.TelegramUser fromUser = new TelegramModels.TelegramUser(
                111L, false, "User", null, null
        );
        TelegramModels.TelegramChat chat = new TelegramModels.TelegramChat(
                111L, "private", null, null, "User", null
        );
        TelegramModels.TelegramMessage msg = new TelegramModels.TelegramMessage(
                10L, fromUser, chat, 1700000000L, "/start", null
        );

        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(401L, msg));
        telegramLinkService.processUpdate(new TelegramModels.TelegramUpdate(401L, msg)); // Replay

        // Only processed once
        verify(mockTelegramApiClient, times(1)).sendMessage(eq(111L), anyString(), any(), any());
    }

    @Test
    @DisplayName("End-to-end OTP delivery via Telegram for linked user successfully registers new account")
    void fullAuthFlow_LinkedUser_DispatchesOtpViaTelegramAndAuthenticates() throws Exception {
        String phone = "+998901234567";
        Long chatId = 987654L;
        Long userId = 12345L;

        // Establish linked Telegram chat
        userLinkRepository.save(new TelegramUserLinkEntity(phone, chatId, userId, "fermer_vali", "Vali", "Aliyev"));

        // 1. Request OTP
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.expiresInSeconds").value(180))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));

        // Verify Telegram message was dispatched with the 6-digit code
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTelegramApiClient).sendMessage(eq(chatId), messageCaptor.capture(), eq("Markdown"), isNull());

        String sentText = messageCaptor.getValue();
        assertTrue(sentText.contains("Hosil"));
        assertTrue(sentText.contains("tasdiqlash kodi"));

        // Extract 6-digit OTP code from message
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(sentText);
        assertTrue(matcher.find(), "Sent message must contain 6-digit OTP code");
        String plainOtp = matcher.group();

        // 2. Verify OTP and receive JWT tokens
        var challenge = otpChallengeRepository.findByPhoneNumberOrderByCreatedAtDesc(phone).get(0);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(
                                challenge.getId(),
                                plainOtp,
                                "Android-Pixel-Device",
                                "HosilMobileClient/1.0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.user.phoneNumber").value(phone))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"));

        // Verify user was registered in platform
        UserEntity createdUser = userRepository.findByPhoneNumber(phone).orElseThrow();
        assertEquals("ACTIVE", createdUser.getStatus());
    }

    @Test
    @DisplayName("Checking link status via POST /api/v1/auth/telegram/status with sessionToken and anti-enumeration safeguards")
    void checkLinkStatus_PostWithSessionToken_EnforcesSecurityAndAntiEnumeration() throws Exception {
        String phone = "+998905556677";

        // 1. Regression test: Legacy GET /api/v1/auth/telegram/status?phoneNumber=... is rejected with 405 Method Not Allowed
        mockMvc.perform(get("/api/v1/auth/telegram/status")
                        .param("phoneNumber", phone))
                .andExpect(status().isMethodNotAllowed());

        // 2. Regression test: POST with phoneNumber query or body without sessionToken is rejected with 400 Bad Request
        mockMvc.perform(post("/api/v1/auth/telegram/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 3. Regression test: Blank sessionToken is rejected
        mockMvc.perform(post("/api/v1/auth/telegram/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TelegramLinkStatusRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 4. Regression test: Invalid/non-existent sessionToken is rejected safely
        mockMvc.perform(post("/api/v1/auth/telegram/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TelegramLinkStatusRequest("invalid_dummy_token_999"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 5. Valid session before linking: returns isLinked = false without disclosing phone or Telegram details
        var session = telegramLinkService.createLinkingSession(phone);
        mockMvc.perform(post("/api/v1/auth/telegram/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TelegramLinkStatusRequest(session.token()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLinked").value(false))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.telegramUserId").doesNotExist());

        // 6. After linking establishes: returns isLinked = true
        userLinkRepository.save(new TelegramUserLinkEntity(phone, 777L, 888L, null, null, null));
        mockMvc.perform(post("/api/v1/auth/telegram/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TelegramLinkStatusRequest(session.token()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLinked").value(true))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.telegramUserId").doesNotExist());

        // 7. Regression test: Expired unconsumed session is rejected
        String expiredPhone = "+998909998877";
        String rawExpiredToken = "expired_raw_token_xyz_123456789012";
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(rawExpiredToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expiredHash = java.util.HexFormat.of().formatHex(digest);
        sessionRepository.save(new TelegramLinkingSessionEntity(expiredPhone, expiredHash, java.time.Instant.now().minusSeconds(300)));

        mockMvc.perform(post("/api/v1/auth/telegram/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TelegramLinkStatusRequest(rawExpiredToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
