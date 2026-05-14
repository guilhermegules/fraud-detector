package com.rinha.frauddetector.http;

import com.rinha.frauddetector.domain.NormalizationConstants;
import com.rinha.frauddetector.dto.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonCodec {

    private JsonCodec() {}

    private static final byte[] READY_BYTES = "{\"status\":\"Ready\"}".getBytes(StandardCharsets.UTF_8);

    public static byte[] readyResponse() {
        return READY_BYTES;
    }

    public static FraudRequest parseFraudRequest(byte[] data) {
        return new Parser(data).parseRequest();
    }

    public static NormalizationConstants parseNormalization(byte[] data) {
        Parser p = new Parser(data);
        p.expect('{');
        float maxAmount = 0, maxInstallments = 0, amountVsAvgRatio = 0, maxMinutes = 0, maxKm = 0, maxTxCount24h = 0, maxMerchantAvgAmount = 0;
        while (p.pos < p.data.length && p.data[p.pos] != '}') {
            String key = p.parseString();
            p.expect(':');
            float val = p.parseFloat();
            switch (key) {
                case "max_amount" -> maxAmount = val;
                case "max_installments" -> maxInstallments = val;
                case "amount_vs_avg_ratio" -> amountVsAvgRatio = val;
                case "max_minutes" -> maxMinutes = val;
                case "max_km" -> maxKm = val;
                case "max_tx_count_24h" -> maxTxCount24h = val;
                case "max_merchant_avg_amount" -> maxMerchantAvgAmount = val;
            }
            p.skipComma();
        }
        return new NormalizationConstants(maxAmount, maxInstallments, amountVsAvgRatio,
                maxMinutes, maxKm, maxTxCount24h, maxMerchantAvgAmount);
    }

    public static Map<String, Float> parseMccRisk(byte[] data) {
        Parser p = new Parser(data);
        p.expect('{');
        Map<String, Float> map = new HashMap<>();
        while (p.pos < p.data.length && p.data[p.pos] != '}') {
            String key = p.parseString();
            p.expect(':');
            float val = p.parseFloat();
            map.put(key, val);
            p.skipComma();
        }
        return map;
    }

    private static final class Parser {
        final byte[] data;
        int pos;

        Parser(byte[] data) {
            this.data = data;
            this.pos = 0;
        }

        void skipWs() {
            while (pos < data.length && data[pos] <= 0x20) {
                pos++;
            }
        }

        void expect(char c) {
            skipWs();
            if (pos >= data.length || data[pos] != c) {
                throw new JsonParseException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        void skipComma() {
            skipWs();
            if (pos < data.length && data[pos] == ',') {
                pos++;
            }
        }

        String parseString() {
            skipWs();
            if (pos >= data.length || data[pos] != '"') {
                throw new JsonParseException("Expected string at position " + pos);
            }
            pos++;
            int start = pos;
            while (pos < data.length && data[pos] != '"') {
                if (data[pos] == '\\') {
                    pos++;
                }
                pos++;
            }
            if (pos >= data.length) {
                throw new JsonParseException("Unterminated string");
            }
            String s = new String(data, start, pos - start, StandardCharsets.UTF_8);
            pos++;
            return s;
        }

        float parseFloat() {
            skipWs();
            int start = pos;
            if (pos < data.length && data[pos] == '-') pos++;
            while (pos < data.length && data[pos] >= '0' && data[pos] <= '9') pos++;
            if (pos < data.length && data[pos] == '.') pos++;
            while (pos < data.length && data[pos] >= '0' && data[pos] <= '9') pos++;
            return Float.parseFloat(new String(data, start, pos - start, StandardCharsets.US_ASCII));
        }

        void skipValue() {
            skipWs();
            if (pos >= data.length) return;
            byte c = data[pos];
            switch (c) {
                case '"' -> parseString();
                case '{' -> {
                    pos++;
                    while (pos < data.length && data[pos] != '}') {
                        skipValue();
                        skipComma();
                    }
                    if (pos < data.length) pos++;
                }
                case '[' -> {
                    pos++;
                    while (pos < data.length && data[pos] != ']') {
                        skipValue();
                        skipComma();
                    }
                    if (pos < data.length) pos++;
                }
                case 't', 'f', 'n' -> {
                    while (pos < data.length && data[pos] >= 'a' && data[pos] <= 'z') pos++;
                }
                default -> {
                    if (data[pos] == '-' || (data[pos] >= '0' && data[pos] <= '9')) {
                        parseFloat();
                    }
                }
            }
        }

        FraudRequest parseRequest() {
            expect('{');
            String id = null;
            TransactionDTO transaction = null;
            CustomerDTO customer = null;
            MerchantDTO merchant = null;
            TerminalDTO terminal = null;
            LastTransactionDTO lastTransaction = null;

            while (pos < data.length && data[pos] != '}') {
                String key = parseString();
                expect(':');
                switch (key) {
                    case "id" -> id = parseString();
                    case "transaction" -> transaction = parseTransaction();
                    case "customer" -> customer = parseCustomer();
                    case "merchant" -> merchant = parseMerchant();
                    case "terminal" -> terminal = parseTerminal();
                    case "last_transaction" -> lastTransaction = parseLastTransaction();
                    default -> skipValue();
                }
                skipComma();
            }
            return new FraudRequest(id, transaction, customer, merchant, terminal, lastTransaction);
        }

        TransactionDTO parseTransaction() {
            expect('{');
            float amount = 0;
            int installments = 0;
            String requestedAt = null;
            while (pos < data.length && data[pos] != '}') {
                String key = parseString();
                expect(':');
                switch (key) {
                    case "amount" -> amount = parseFloat();
                    case "installments" -> installments = (int) parseFloat();
                    case "requested_at" -> requestedAt = parseString();
                    default -> skipValue();
                }
                skipComma();
            }
            pos++;
            return new TransactionDTO(amount, installments, requestedAt);
        }

        CustomerDTO parseCustomer() {
            expect('{');
            float avgAmount = 0;
            int txCount24h = 0;
            List<String> knownMerchants = List.of();
            while (pos < data.length && data[pos] != '}') {
                String key = parseString();
                expect(':');
                switch (key) {
                    case "avg_amount" -> avgAmount = parseFloat();
                    case "tx_count_24h" -> txCount24h = (int) parseFloat();
                    case "known_merchants" -> knownMerchants = parseStringArray();
                    default -> skipValue();
                }
                skipComma();
            }
            pos++;
            return new CustomerDTO(avgAmount, txCount24h, knownMerchants);
        }

        MerchantDTO parseMerchant() {
            expect('{');
            String id = null;
            String mcc = null;
            float avgAmount = 0;
            while (pos < data.length && data[pos] != '}') {
                String key = parseString();
                expect(':');
                switch (key) {
                    case "id" -> id = parseString();
                    case "mcc" -> mcc = parseString();
                    case "avg_amount" -> avgAmount = parseFloat();
                    default -> skipValue();
                }
                skipComma();
            }
            pos++;
            return new MerchantDTO(id, mcc, avgAmount);
        }

        TerminalDTO parseTerminal() {
            expect('{');
            boolean isOnline = false;
            boolean cardPresent = false;
            float kmFromHome = 0;
            while (pos < data.length && data[pos] != '}') {
                String key = parseString();
                expect(':');
                switch (key) {
                    case "is_online" -> isOnline = parseBoolean();
                    case "card_present" -> cardPresent = parseBoolean();
                    case "km_from_home" -> kmFromHome = parseFloat();
                    default -> skipValue();
                }
                skipComma();
            }
            pos++;
            return new TerminalDTO(isOnline, cardPresent, kmFromHome);
        }

        LastTransactionDTO parseLastTransaction() {
            skipWs();
            if (pos < data.length && data[pos] == 'n') {
                parseNull();
                return null;
            }
            expect('{');
            float kmFromCurrent = 0;
            String timestamp = null;
            while (pos < data.length && data[pos] != '}') {
                String key = parseString();
                expect(':');
                switch (key) {
                    case "km_from_current" -> kmFromCurrent = parseFloat();
                    case "requested_at" -> timestamp = parseString();
                    default -> skipValue();
                }
                skipComma();
            }
            pos++;
            return new LastTransactionDTO(timestamp, kmFromCurrent);
        }

        List<String> parseStringArray() {
            expect('[');
            List<String> list = new ArrayList<>();
            while (pos < data.length && data[pos] != ']') {
                list.add(parseString());
                skipComma();
            }
            expect(']');
            return list;
        }

        boolean parseBoolean() {
            skipWs();
            if (pos + 4 <= data.length && data[pos] == 't' && data[pos + 1] == 'r'
                    && data[pos + 2] == 'u' && data[pos + 3] == 'e') {
                pos += 4;
                return true;
            }
            if (pos + 5 <= data.length && data[pos] == 'f' && data[pos + 1] == 'a'
                    && data[pos + 2] == 'l' && data[pos + 3] == 's' && data[pos + 4] == 'e') {
                pos += 5;
                return false;
            }
            throw new JsonParseException("Expected boolean at position " + pos);
        }

        void parseNull() {
            skipWs();
            if (pos + 4 > data.length || data[pos] != 'n' || data[pos + 1] != 'u'
                    || data[pos + 2] != 'l' || data[pos + 3] != 'l') {
                throw new JsonParseException("Expected null at position " + pos);
            }
            pos += 4;
        }
    }

    public static final class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }
}
