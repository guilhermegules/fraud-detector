package com.rinha.frauddetector.dto;

import java.util.List;

public record FraudRequest(
    String id,
    TransactionDTO transaction,
    CustomerDTO customer,
    MerchantDTO merchant,
    TerminalDTO terminal,
    LastTransactionDTO last_transaction
) {}
