package com.nusantara.banking.web;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminHoldReadController {
  private final JdbcTemplate db;

  public AdminHoldReadController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping("/api/v1/admin/accounts/{id}/holds")
  public List<Map<String, Object>> holds(@PathVariable UUID id) {
    return db.queryForList(
        "select id,amount,reason,released_at from account_holds where account_id=? order by"
            + " released_at nulls first,id limit 100",
        id);
  }
}
