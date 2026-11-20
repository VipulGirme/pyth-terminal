package com.pyth.dashboard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api")
public class PriceController {

    // 1. FIELD - this is what was missing / not resolving
    private final RestClient hermesClient;
    private final long maxAgeMs;
    private final double maxConfPct;
    private static final Map<String, String> FEEDS = Map.of(
            "BTC", "e62df6c8b4a85fe1a67db44dc12de5db330f7ac66b72dc658afedf0f4a415b43",
            "ETH", "ff61491a931112ddf1bd8147cd1b641375f79f5825126d665480874634fd0ace",
            "SOL", "ef0d8b6fda2ceba41da15d4095d1da392a0d2f8ed0c6c7bc0f4cfac8c280b56d"
    );
    // 2. CONSTRUCTOR - assign to this.hermesClient
    public PriceController(@Value("${pyth.hermes.url}") String url,
                           @Value("${pyth.api.key}") String key,
                           @Value("${pyth.max-age-ms}") long maxAgeMs,
                           @Value("${pyth.max-confidence-pct}") double maxConfPct) {
        System.out.println("PYTH KEY LOADED? length=" + (key == null ? "null" : key.length()) + " first4=" + (key != null && key.length() > 4 ? key.substring(0,4) : "none"));
        this.hermesClient = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + key)
                .build();
        this.maxAgeMs = maxAgeMs;
        this.maxConfPct = maxConfPct;
    }

    @GetMapping("/prices")
    public List<PriceDto> prices() {

        String ids = String.join("&", FEEDS.values().stream()
                .map(id -> "ids[]=" + id).toList());

        // 3. USE hermesClient, NOT hermes
        HermesResponse res = hermesClient.get()
                .uri("/v2/updates/price/latest?" + ids)
                .retrieve()
                .body(HermesResponse.class);

        List<PriceDto> out = new ArrayList<>();
        if (res == null || res.parsed() == null) return out;

        for (Parsed p : res.parsed()) {
            String cleanId = p.id().toLowerCase().replace("0x", "");
            String symbol = FEEDS.entrySet().stream()
                    .filter(e -> e.getValue().toLowerCase().replace("0x", "").equals(cleanId))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse("?");
            out.add(toDto(symbol, p));
        }
        return out;
    }

    private PriceDto toDto(String symbol, Parsed p) {
        // FIX: use scaleByPowerOfTen, NOT pow() — pow() breaks on negative expo
        BigDecimal price = new BigDecimal(p.price().price())
                .scaleByPowerOfTen(p.price().expo());
        BigDecimal conf = new BigDecimal(p.price().conf())
                .scaleByPowerOfTen(p.price().expo());

        long ageMs = System.currentTimeMillis() - p.price().publish_time() * 1000L;
        double confPct = price.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                conf.divide(price.abs(), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

        boolean stale = ageMs > maxAgeMs || confPct > maxConfPct;
        String reason = stale
                ? (ageMs > maxAgeMs ? "STALE: " + ageMs + "ms old" : "LOW CONFIDENCE: " + confPct + "%")
                : "FRESH";

        return new PriceDto(symbol, price, conf, confPct, ageMs, p.price().publish_time(), stale, reason);
    }
    @PostMapping("/quote")
    public QuoteDto quote(@RequestBody QuoteReq req) {
        PriceDto price = prices().stream()
                .filter(p -> p.symbol().equalsIgnoreCase(req.symbol()))
                .findFirst().orElseThrow();

        if (price.stale()) {
            throw new PriceStaleException("Trade rejected: " + price.reason());
        }
        BigDecimal total = price.price().multiply(BigDecimal.valueOf(req.amount()));
        return new QuoteDto(req.symbol(), price.price(), req.amount(), total, price.publishTime(), "OK");
    }

    record HermesResponse(List<Parsed> parsed) {}
    record Parsed(String id, Price price) {}
    record Price(String price, String conf, int expo, long publish_time) {}
    record PriceDto(String symbol, BigDecimal price, BigDecimal confidence,
                    double confidencePct, long ageMs, long publishTime, boolean stale, String reason) {}
    record QuoteReq(String symbol, double amount) {}
    record QuoteDto(String symbol, BigDecimal unitPrice, double amount,
                    BigDecimal total, long publishTime, String status) {}

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    static class PriceStaleException extends RuntimeException {
        PriceStaleException(String m) { super(m); }
    }
}