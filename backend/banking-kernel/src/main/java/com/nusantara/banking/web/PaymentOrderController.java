package com.nusantara.banking.web;

import com.nusantara.banking.dto.*;
import com.nusantara.banking.dto.BankingResponses.Order;
import com.nusantara.banking.security.CurrentCustomer;
import com.nusantara.banking.service.PaymentOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-orders")
@Validated
public class PaymentOrderController {
  private final PaymentOrderService orders;
  private final CurrentCustomer current;
  private final JdbcTemplate db;

  public PaymentOrderController(
      PaymentOrderService orders, CurrentCustomer current, JdbcTemplate db) {
    this.orders = orders;
    this.current = current;
    this.db = db;
  }

  @PostMapping
  public Order create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody OrderRequest r) {
    return Order.from(orders.create(current.id(jwt), key, r));
  }

  @GetMapping
  public PageResult<Order> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    var customer = current.id(jwt);
    return new PageResult<>(
        db
            .queryForList(
                "select * from payment_orders where customer_id=? order by created_at desc limit ?"
                    + " offset ?",
                customer,
                size,
                (long) page * size)
            .stream()
            .map(Order::from)
            .toList(),
        page,
        size,
        db.queryForObject(
            "select count(*) from payment_orders where customer_id=?", Long.class, customer));
  }

  @GetMapping("/{id}")
  public Order get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return Order.from(orders.get(current.id(jwt), id));
  }

  @PostMapping("/{id}/cancel")
  public Order cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return Order.from(orders.cancel(current.id(jwt), id));
  }
}
