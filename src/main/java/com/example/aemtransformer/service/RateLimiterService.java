package com.example.aemtransformer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    @Value("${rate.wp.requests-per-second:0}")
    private double wpRps;

    @Value("${rate.aem.requests-per-second:0}")
    private double aemRps;

    private final Map<String, Long> nextAllowedTime = new ConcurrentHashMap<>();

    public void acquireWp() {
        acquire("wp", wpRps);
    }

    public void acquireAem() {
        acquire("aem", aemRps);
    }

    private void acquire(String key, double rps) {
        if (rps <= 0) {
            return;
        }
        long intervalNanos = (long) (1_000_000_000L / rps);
        long now = System.nanoTime();
        long next = nextAllowedTime.getOrDefault(key, now);
        if (next > now) {
            long sleepNanos = next - now;
            try {
                Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            now = System.nanoTime();
        }
        nextAllowedTime.put(key, now + intervalNanos);
    }
}
