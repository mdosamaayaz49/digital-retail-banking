package com.nusantara.banking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nusantara.banking.dto.TransferRequest;
import com.nusantara.banking.service.*;
import com.nusantara.banking.web.BankingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "banking.outbox-enabled=false",
      "banking.orders-enabled=false",
      "logging.structured.format.console="
    })
@org.springframework.context.annotation.Import({
  com.nusantara.banking.web.CustomerController.class,
  com.nusantara.banking.web.AccountController.class,
  com.nusantara.banking.web.AccountLifecycleController.class,
  com.nusantara.banking.web.StatementController.class
})
@AutoConfigureMockMvc
@Testcontainers
class BankingIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired TransferService transfers;
  @Autowired StatementService statements;
  @Autowired MockMvc mvc;
  static UUID C = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      D = UUID.fromString("00000000-0000-0000-0000-000000000002"),
      A = UUID.fromString("10000000-0000-0000-0000-000000000001"),
      B = UUID.fromString("10000000-0000-0000-0000-000000000002"),
      R = UUID.fromString("10000000-0000-0000-0000-000000000003");

  @Autowired PaymentOrderService orders;
  @Autowired AccountOperations operations;

  @BeforeEach
  void reset() {
    db.execute(
        "truncate"
            + " notification_records,outbox_events,audit_logs,bank_statements,account_holds,ledger_entries,transfers,beneficiaries,bank_accounts,customers"
            + " cascade");
    var setup =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                Objects.requireNonNull(db.getDataSource())));
    setup.execute(
        status -> {
          new ResourceDatabasePopulator(
                  new ClassPathResource("db/migration/V2__fictional_demo.sql"),
                  new ClassPathResource("demo-aliases.sql"))
              .execute(Objects.requireNonNull(db.getDataSource()));
          return null;
        });
  }

  TransferRequest request(String amount) {
    return new TransferRequest(A, R, new BigDecimal(amount), "Lunch payment");
  }

  BigDecimal balance(UUID id) {
    return db.queryForObject("select balance from bank_accounts where id=?", BigDecimal.class, id);
  }

  void checkReconciled() {
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from bank_accounts a where a.balance<>(select"
                + " coalesce(sum(l.amount),0) from ledger_entries l where l.account_id=a.id)",
            Integer.class));
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from (select transfer_id from ledger_entries group by transfer_id"
                + " having sum(amount)<>0 or count(*)<>2) bad",
            Integer.class));
  }

  @Test
  void settlesAtomicBalancedTransfer() {
    var result = transfers.transfer(C, UUID.randomUUID(), request("500.00"));
    assertEquals("SETTLED", result.get("status"));
    assertEquals(new BigDecimal("12350.00"), balance(A));
    assertEquals(new BigDecimal("3700.00"), balance(R));
    assertEquals(
        2,
        db.queryForObject(
            "select count(*) from ledger_entries where transfer_id=?",
            Integer.class,
            result.get("id")));
    assertEquals(1, db.queryForObject("select count(*) from audit_logs", Integer.class));
    assertEquals(2, db.queryForObject("select count(*) from outbox_events", Integer.class));
    checkReconciled();
  }

  @Test
  void ownAccountTransfer() {
    transfers.transfer(
        C, UUID.randomUUID(), new TransferRequest(A, B, new BigDecimal("50.00"), "Savings"));
    assertEquals(new BigDecimal("5730.50"), balance(B));
    checkReconciled();
  }

  @Test
  void insufficientFundsLeavesNoPartialPosting() {
    var e =
        assertThrows(
            BankingException.class,
            () ->
                transfers.transfer(
                    C,
                    UUID.randomUUID(),
                    new TransferRequest(B, A, new BigDecimal("9000.00"), "Test")));
    assertEquals("INSUFFICIENT_FUNDS", e.code);
    assertEquals(new BigDecimal("5680.50"), balance(B));
    assertEquals(3, db.queryForObject("select count(*) from transfers", Integer.class));
    checkReconciled();
  }

  @Test
  void duplicateKeyReturnsOriginal() {
    var key = UUID.randomUUID();
    var first = transfers.transfer(C, key, request("500.00"));
    var second = transfers.transfer(C, key, request("500.0"));
    assertEquals(first.get("id"), second.get("id"));
    assertEquals(new BigDecimal("12350.00"), balance(A));
    checkReconciled();
  }

  @Test
  void duplicateKeyChangedPayloadConflicts() {
    var key = UUID.randomUUID();
    transfers.transfer(C, key, request("500.00"));
    assertEquals(
        "IDEMPOTENCY_CONFLICT",
        assertThrows(BankingException.class, () -> transfers.transfer(C, key, request("600.00")))
            .code);
  }

  @Test
  void beneficiaryRequired() {
    db.update("delete from beneficiaries");
    assertEquals(
        "INVALID_BENEFICIARY",
        assertThrows(
                BankingException.class,
                () -> transfers.transfer(C, UUID.randomUUID(), request("50.00")))
            .code);
    checkReconciled();
  }

  @Test
  void frozenAccountCannotTransfer() {
    db.update("update bank_accounts set status='FROZEN' where id=?", R);
    assertEquals(
        "ACCOUNT_INACTIVE",
        assertThrows(
                BankingException.class,
                () -> transfers.transfer(C, UUID.randomUUID(), request("50.00")))
            .code);
    checkReconciled();
  }

  @Test
  void pendingKycCannotTransfer() {
    assertEquals(
        "KYC_REQUIRED",
        assertThrows(
                BankingException.class,
                () ->
                    transfers.transfer(
                        D,
                        UUID.randomUUID(),
                        new TransferRequest(R, A, new BigDecimal("50.00"), "Test")))
            .code);
  }

  @Test
  void invalidPrecisionRejected() {
    assertEquals(
        "INVALID_AMOUNT",
        assertThrows(
                BankingException.class,
                () -> transfers.transfer(C, UUID.randomUUID(), request("1.001")))
            .code);
  }

  @Test
  void concurrentTransfersCannotOverspend() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var latch = new CountDownLatch(1);
      Callable<Boolean> task =
          () -> {
            latch.await();
            try {
              transfers.transfer(C, UUID.randomUUID(), request("9000.00"));
              return true;
            } catch (BankingException e) {
              assertEquals("INSUFFICIENT_FUNDS", e.code);
              return false;
            }
          };
      var one = pool.submit(task);
      var two = pool.submit(task);
      latch.countDown();
      assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
      assertEquals(new BigDecimal("3850.00"), balance(A));
      checkReconciled();
    }
  }

  @Test
  void concurrentDuplicateSettlesOnce() throws Exception {
    var key = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> transfers.transfer(C, key, request("500.00")));
      var two = pool.submit(() -> transfers.transfer(C, key, request("500.00")));
      assertEquals(
          one.get(15, TimeUnit.SECONDS).get("id"), two.get(15, TimeUnit.SECONDS).get("id"));
      assertEquals(new BigDecimal("12350.00"), balance(A));
      checkReconciled();
    }
  }

  @Test
  void rollsBackLedgerWhenOutboxInsertFails() {
    db.execute(
        "create function test_fail() returns trigger language plpgsql as $$ begin raise exception"
            + " 'Injected outbox failure'; end $$");
    db.execute(
        "create trigger fail_outbox before insert on outbox_events for each row execute function"
            + " test_fail()");
    try {
      assertThrows(
          Exception.class, () -> transfers.transfer(C, UUID.randomUUID(), request("500.00")));
      assertEquals(new BigDecimal("12850.00"), balance(A));
      assertEquals(6, db.queryForObject("select count(*) from ledger_entries", Integer.class));
      checkReconciled();
    } finally {
      db.execute("drop trigger fail_outbox on outbox_events");
      db.execute("drop function test_fail()");
    }
  }

  @Test
  void reversalIsCompensatingAndIdempotent() {
    var original = transfers.transfer(C, UUID.randomUUID(), request("500.00"));
    var id = (UUID) original.get("id");
    var reversal = transfers.reverse(id, "admin");
    assertEquals(reversal.get("id"), transfers.reverse(id, "admin").get("id"));
    assertEquals(new BigDecimal("12850.00"), balance(A));
    assertEquals(10, db.queryForObject("select count(*) from ledger_entries", Integer.class));
    assertEquals("REVERSED", transfers.byId(id).get("status"));
    checkReconciled();
  }

  @Test
  void immutableLedgerAndAudit() {
    transfers.transfer(C, UUID.randomUUID(), request("1.00"));
    assertThrows(Exception.class, () -> db.update("delete from ledger_entries"));
    assertThrows(Exception.class, () -> db.update("update audit_logs set action='edited'"));
  }

  @Test
  void databaseRejectsUnbalancedEntries() {
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                Objects.requireNonNull(db.getDataSource())));
    assertThrows(
        Exception.class,
        () ->
            tx.execute(
                status -> {
                  db.update(
                      "insert into ledger_entries(transfer_id,account_id,amount) values (?,?,1)",
                      UUID.fromString("30000000-0000-0000-0000-000000000001"),
                      B);
                  return null;
                }));
    checkReconciled();
  }

  @Test
  void unauthenticatedRequestRejected() throws Exception {
    mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
  }

  @Test
  void unauthorizedAccountHidden() throws Exception {
    mvc.perform(
            get("/api/v1/accounts/" + R)
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonAdminCannotReview() throws Exception {
    mvc.perform(
            get("/api/v1/admin/customers")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanReview() throws Exception {
    mvc.perform(
            get("/api/v1/admin/customers")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  void sourceOwnershipEnforced() {
    db.update("update customers set kyc_status='VERIFIED' where id=?", D);
    assertEquals(
        "ACCOUNT_NOT_FOUND",
        assertThrows(
                BankingException.class,
                () -> transfers.transfer(D, UUID.randomUUID(), request("1.00")))
            .code);
  }

  @Test
  void statementAuthorizationAndPdf() throws Exception {
    var now = LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"));
    var statement = statements.generate(C, A, now.minusMonths(4), now);
    var id = (UUID) statement.get("id");
    var bytes = statements.pdf(C, id);
    assertTrue(new String(bytes, 0, 4).equals("%PDF"));
    assertEquals(404, assertThrows(BankingException.class, () -> statements.pdf(D, id)).status);
    mvc.perform(
            get("/api/v1/statements/" + id + "/download")
                .with(jwt().jwt(j -> j.subject("22222222-2222-2222-2222-222222222222"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void httpValidationRejectsNegativeAmount() throws Exception {
    mvc.perform(
            post("/api/v1/transfers")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111")))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(
                    "{\"sourceId\":\""
                        + A
                        + "\",\"destinationId\":\""
                        + R
                        + "\",\"amount\":\"-1.00\",\"description\":\"Test\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void databaseRejectsBalanceWithoutPostings() {
    assertThrows(
        Exception.class,
        () -> db.update("update bank_accounts set balance=balance+1 where id=?", A));
    checkReconciled();
  }

  @Test
  void dailyLimitIsAtomicAndUsesGrossOutgoing() {
    transfers.transfer(C, UUID.randomUUID(), request("10000.00"));
    transfers.transfer(
        C, UUID.randomUUID(), new TransferRequest(B, A, new BigDecimal("5000.00"), "Move savings"));
    transfers.transfer(C, UUID.randomUUID(), request("7500.00"));
    // Move funds back with an ADMIN compensation. It does not reset the customer's gross limit.
    var original =
        db.queryForObject(
            "select id from transfers where customer_id=? and amount=10000", UUID.class, C);
    transfers.reverse(original, "admin");
    assertEquals(
        "DAILY_LIMIT",
        assertThrows(
                BankingException.class,
                () -> transfers.transfer(C, UUID.randomUUID(), request("3000.00")))
            .code);
    checkReconciled();
  }

  @Test
  void profileCannotEditKyc() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/customers/me")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111")))
                .contentType("application/json")
                .content(
                    "{\"displayName\":\"Amira"
                        + " Demo\",\"email\":\"amira@example.test\",\"phone\":\"+60 10 000"
                        + " 0001\",\"kyc_status\":\"REJECTED\"}"))
        .andExpect(status().isOk());
    assertEquals(
        "VERIFIED",
        db.queryForObject("select kyc_status from customers where id=?", String.class, C));
  }

  @Test
  void beneficiaryManagementEnforcesOwnership() throws Exception {
    var id = UUID.fromString("20000000-0000-0000-0000-000000000001");
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/beneficiaries/" + id)
                .with(jwt().jwt(j -> j.subject("22222222-2222-2222-2222-222222222222"))))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/beneficiaries")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$[0].nickname")
                .value("Daniel Tan"));
  }

  @Test
  void transactionPaginationAndSearch() throws Exception {
    transfers.transfer(C, UUID.randomUUID(), request("5.00"));
    mvc.perform(
            get("/api/v1/transactions?search=Lunch&size=1")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total")
                .value(1))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.items[0].amount")
                .value(-5));
    mvc.perform(
            get("/api/v1/transactions?size=101")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
        .andExpect(status().isBadRequest());
  }

  com.nusantara.banking.dto.OrderRequest order(String channel, String alias) {
    return new com.nusantara.banking.dto.OrderRequest(
        A,
        R,
        new BigDecimal("500.00"),
        "Scheduled lunch",
        channel,
        alias,
        java.time.Instant.now().minusSeconds(2));
  }

  @Test
  void openAndCloseZeroBalanceAccount() {
    var a = operations.open(C, "SAVINGS", "Rainy day");
    var id = (UUID) a.get("id");
    assertEquals(BigDecimal.ZERO.setScale(2), balance(id));
    operations.close(C, id);
    assertEquals(
        "CLOSED",
        db.queryForObject("select status from bank_accounts where id=?", String.class, id));
    checkReconciled();
  }

  @Test
  void fundedAccountCannotClose() {
    assertEquals(
        "ACCOUNT_NOT_EMPTY",
        assertThrows(BankingException.class, () -> operations.close(C, A)).code);
  }

  @Test
  void holdProtectsAvailableFundsAndReleaseIsIdempotent() {
    var h = operations.hold(A, UUID.randomUUID(), new BigDecimal("10000.00"), "Review", "admin");
    assertEquals(
        "INSUFFICIENT_FUNDS",
        assertThrows(
                BankingException.class,
                () -> transfers.transfer(C, UUID.randomUUID(), request("3000.00")))
            .code);
    operations.release((UUID) h.get("id"), "admin");
    operations.release((UUID) h.get("id"), "admin");
    transfers.transfer(C, UUID.randomUUID(), request("3000.00"));
    checkReconciled();
  }

  @Test
  void pendingOrderReservesAndCancellationReleases() {
    var key = UUID.randomUUID();
    var req = order("SCHEDULED", null);
    var first = orders.create(C, key, req);
    assertEquals(first.get("id"), orders.create(C, key, req).get("id"));
    assertEquals(
        new BigDecimal("500.00"),
        db.queryForObject("select held from bank_accounts where id=?", BigDecimal.class, A));
    orders.cancel(C, (UUID) first.get("id"));
    orders.cancel(C, (UUID) first.get("id"));
    assertEquals(
        BigDecimal.ZERO.setScale(2),
        db.queryForObject("select held from bank_accounts where id=?", BigDecimal.class, A));
    assertEquals(new BigDecimal("12850.00"), balance(A));
    checkReconciled();
  }

  @Test
  void scheduledSettlementPostsOnce() {
    var o = orders.create(C, UUID.randomUUID(), order("SCHEDULED", null));
    var id = (UUID) o.get("id");
    orders.process(C, id);
    orders.process(C, id);
    assertEquals("SETTLED", orders.get(C, id).get("status"));
    assertEquals(new BigDecimal("12350.00"), balance(A));
    assertEquals(
        "ALREADY_PROCESSED", assertThrows(BankingException.class, () -> orders.cancel(C, id)).code);
    checkReconciled();
  }

  @Test
  void mockNetworkAcceptsWithReconciledReference() {
    var o = orders.create(C, UUID.randomUUID(), order("DUITNOW_MOCK", "daniel@example.test"));
    var id = (UUID) o.get("id");
    orders.process(C, id);
    assertEquals("SETTLED", orders.get(C, id).get("status"));
    assertEquals(
        "ACCEPTED",
        db.queryForObject(
            "select outcome from network_messages where order_id=?", String.class, id));
    assertNotNull(orders.get(C, id).get("network_reference"));
    checkReconciled();
  }

  @Test
  void mockNetworkRejectionHasNoPostingAndReleasesFunds() {
    var o = orders.create(C, UUID.randomUUID(), order("DUITNOW_MOCK", "reject@example.test"));
    var id = (UUID) o.get("id");
    orders.process(C, id);
    assertEquals("REJECTED", orders.get(C, id).get("status"));
    assertEquals(new BigDecimal("12850.00"), balance(A));
    assertEquals(
        BigDecimal.ZERO.setScale(2),
        db.queryForObject("select held from bank_accounts where id=?", BigDecimal.class, A));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from outbox_events where event_type='TRANSFER_FAILED'",
            Integer.class));
    checkReconciled();
  }

  @Test
  void futurePaymentCannotSettleEarly() {
    var req =
        new com.nusantara.banking.dto.OrderRequest(
            A,
            R,
            new BigDecimal("500.00"),
            "Tomorrow",
            "SCHEDULED",
            null,
            java.time.Instant.now().plusSeconds(3600));
    var o = orders.create(C, UUID.randomUUID(), req);
    orders.process(C, (UUID) o.get("id"));
    assertEquals("PENDING", orders.get(C, (UUID) o.get("id")).get("status"));
    assertEquals(new BigDecimal("12850.00"), balance(A));
  }

  @Test
  void frozenBeforeSettlementRejectsAndReleases() {
    var o = orders.create(C, UUID.randomUUID(), order("SCHEDULED", null));
    db.update("update bank_accounts set status='FROZEN' where id=?", A);
    orders.process(C, (UUID) o.get("id"));
    assertEquals("REJECTED", orders.get(C, (UUID) o.get("id")).get("status"));
    assertEquals(new BigDecimal("12850.00"), balance(A));
  }

  @Test
  void foreignOrderAndOrderHoldCannotBeMutated() {
    var o = orders.create(C, UUID.randomUUID(), order("SCHEDULED", null));
    assertEquals(
        404,
        assertThrows(BankingException.class, () -> orders.cancel(D, (UUID) o.get("id"))).status);
    assertEquals(
        "ORDER_HOLD",
        assertThrows(
                BankingException.class, () -> operations.release((UUID) o.get("hold_id"), "admin"))
            .code);
  }

  @Test
  void concurrentCancellationAndSettlementStayConsistent() throws Exception {
    var o = orders.create(C, UUID.randomUUID(), order("SCHEDULED", null));
    var id = (UUID) o.get("id");
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                orders.process(C, id);
              });
      var b =
          pool.submit(
              () -> {
                try {
                  orders.cancel(C, id);
                } catch (BankingException e) {
                  assertEquals("ALREADY_PROCESSED", e.code);
                }
              });
      a.get(15, TimeUnit.SECONDS);
      b.get(15, TimeUnit.SECONDS);
    }
    assertTrue(List.of("CANCELLED", "SETTLED").contains(orders.get(C, id).get("status")));
    assertEquals(
        BigDecimal.ZERO.setScale(2),
        db.queryForObject("select held from bank_accounts where id=?", BigDecimal.class, A));
    checkReconciled();
  }

  @Test
  void failedHttpTransferProducesOneCommittedNotification() throws Exception {
    var key = UUID.randomUUID();
    String content =
        "{\"sourceId\":\""
            + B
            + "\",\"destinationId\":\""
            + A
            + "\",\"amount\":\"9000.00\",\"description\":\"Not enough funds\"}";
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/transfers")
                  .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111")))
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content(content))
          .andExpect(status().isUnprocessableEntity());
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from outbox_events where event_type='TRANSFER_FAILED'",
            Integer.class));
    checkReconciled();
  }

  @Test
  void readOnlySupportCanReconcileButCannotMutate() throws Exception {
    mvc.perform(
            get("/api/v1/admin/reconciliation")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.balanced")
                .value(true));
    mvc.perform(
            post("/api/v1/admin/transfers/" + UUID.randomUUID() + "/reverse")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPPORT"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void ledgerAnalyticsExcludeOwnAccountMovements() throws Exception {
    transfers.transfer(
        C, UUID.randomUUID(), new TransferRequest(A, B, new BigDecimal("75.00"), "Own account"));
    transfers.transfer(C, UUID.randomUUID(), request("12.34"));
    mvc.perform(
            get("/api/v1/analytics?months=1")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$[0].spending")
                .value(12.34));
  }

  @Test
  void reportsIncludeGrossAndCompensationSeparately() throws Exception {
    var t = transfers.transfer(C, UUID.randomUUID(), request("50.00"));
    transfers.reverse((UUID) t.get("id"), "admin");
    var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"));
    mvc.perform(
            get("/api/v1/admin/reports?from=" + today + "&to=" + today)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.summary.gross_amount")
                .value(50))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.summary.compensation_amount")
                .value(50));
  }

  @Test
  void closedAccountCannotBeReopened() throws Exception {
    var a = operations.open(C, "CURRENT", "Temporary");
    operations.close(C, (UUID) a.get("id"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/v1/admin/accounts/" + a.get("id") + "/status")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content("{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void databaseRejectsUnpairedHoldMutation() {
    assertThrows(
        Exception.class,
        () ->
            db.update(
                "insert into account_holds(id,customer_id,account_id,amount,reason) values"
                    + " (?,?,?,1,'unpaired')",
                UUID.randomUUID(),
                C,
                A));
  }

  @Test
  void cancelledOrderPayloadChangeConflicts() {
    var key = UUID.randomUUID();
    var req = order("SCHEDULED", null);
    var o = orders.create(C, key, req);
    orders.cancel(C, (UUID) o.get("id"));
    var different =
        new com.nusantara.banking.dto.OrderRequest(
            A,
            R,
            new BigDecimal("600.00"),
            req.description(),
            req.channel(),
            req.alias(),
            req.scheduledAt());
    assertEquals(
        "IDEMPOTENCY_CONFLICT",
        assertThrows(BankingException.class, () -> orders.create(C, key, different)).code);
  }

  @Test
  void concurrentReservationsCannotOverspend() throws Exception {
    var req =
        new com.nusantara.banking.dto.OrderRequest(
            A,
            R,
            new BigDecimal("9000.00"),
            "Reserve",
            "SCHEDULED",
            null,
            java.time.Instant.now().plusSeconds(3600));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Boolean> task =
          () -> {
            try {
              orders.create(C, UUID.randomUUID(), req);
              return true;
            } catch (BankingException e) {
              assertEquals("INSUFFICIENT_FUNDS", e.code);
              return false;
            }
          };
      var one = pool.submit(task);
      var two = pool.submit(task);
      assertNotEquals(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
    }
    assertEquals(
        new BigDecimal("9000.00"),
        db.queryForObject("select held from bank_accounts where id=?", BigDecimal.class, A));
    checkReconciled();
  }

  @Test
  void malaysiaDateFiltersIncludeBothMidnightBoundaries() throws Exception {
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                Objects.requireNonNull(db.getDataSource())));
    for (var instant : List.of("2026-01-16T16:00:00Z", "2026-01-17T15:59:59Z")) {
      tx.execute(
          status -> {
            var id = UUID.randomUUID();
            var time = java.sql.Timestamp.from(java.time.Instant.parse(instant));
            db.update(
                "insert into"
                    + " transfers(id,customer_id,source_id,destination_id,amount,reference,description,status,idempotency_key,request_hash,created_at)"
                    + " values (?,?,?,?,1,?,?,'SETTLED',?,'boundary',?)",
                id,
                C,
                A,
                R,
                "BND-" + id,
                "Midnight boundary",
                UUID.randomUUID(),
                time);
            db.update("update bank_accounts set balance=balance-1 where id=?", A);
            db.update("update bank_accounts set balance=balance+1 where id=?", R);
            db.update(
                "insert into ledger_entries(transfer_id,account_id,amount,created_at) values"
                    + " (?,?,-1,?),(?,?,1,?)",
                id,
                A,
                time,
                id,
                R,
                time);
            return null;
          });
    }
    mvc.perform(
            get("/api/v1/transactions?from=2026-01-17&to=2026-01-17&search=Midnight")
                .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total")
                .value(2));
    mvc.perform(
            get("/api/v1/admin/reports?from=2026-01-17&to=2026-01-17")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.summary.gross_amount")
                .value(2));
  }

  @Test
  void debitedRecipientCanReadCompensatingTransfer() throws Exception {
    var original = transfers.transfer(C, UUID.randomUUID(), request("50.00"));
    var reversal = transfers.reverse((UUID) original.get("id"), "admin");
    mvc.perform(
            get("/api/v1/transfers/" + reversal.get("id"))
                .with(jwt().jwt(j -> j.subject("22222222-2222-2222-2222-222222222222"))))
        .andExpect(status().isOk());
  }

  @Test
  void missingReportParametersAreBadRequest() throws Exception {
    mvc.perform(
            get("/api/v1/admin/reports")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unsupportedMethodReturns405() throws Exception {
    mvc.perform(get("/api/v1/transfers").with(jwt())).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void unsupportedMediaTypeReturns415() throws Exception {
    mvc.perform(
            post("/api/v1/transfers")
                .with(jwt())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/xml")
                .content("<transfer/>"))
        .andExpect(status().isUnsupportedMediaType());
  }
}
