package com.nusantara.banking.web;

public class BankingException extends RuntimeException {
  public final int status;
  public final String code;

  public BankingException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
