package com.nusantara.banking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventService {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public EventService(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public void audit(String actor, String action, UUID resource) {
    db.update(
        "insert into audit_logs(actor,action,resource) values (?,?,?)",
        actor,
        action,
        resource.toString());
  }

  public void event(UUID customer, UUID aggregate, String type, String message) {
    try {
      var id = UUID.randomUUID();
      var payload =
          json.writeValueAsString(
              Map.of(
                  "eventId",
                  id,
                  "customerId",
                  customer,
                  "aggregateId",
                  aggregate,
                  "type",
                  type,
                  "message",
                  message));
      db.update(
          "insert into outbox_events(id,customer_id,aggregate_id,event_type,payload) values"
              + " (?,?,?,?,?)",
          id,
          customer,
          aggregate,
          type,
          payload);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize business event", e);
    }
  }
}
