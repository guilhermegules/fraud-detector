package com.rinha.frauddetector.dto;

public record FraudRequest(
    String id,
    TransactionDTO transaction,
    CustomerDTO customer,
    MerchantDTO merchant,
    TerminalDTO terminal,
    LastTransactionDTO last_transaction
) {}
