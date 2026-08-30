package com.chatling.engine.governance;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrencyControlManagerTest {

    @Test
    public void testAcquireAndRelease() {
        ConcurrencyControlManager manager = new ConcurrencyControlManager();
        String apiKey = "sk-test-concurrency";
        int limit = 3;

        assertTrue(manager.acquire(apiKey, limit)); // 1
        assertTrue(manager.acquire(apiKey, limit)); // 2
        assertTrue(manager.acquire(apiKey, limit)); // 3
        assertEquals(3, manager.getActiveCount(apiKey));

        // 超过并发限制 -> 拒绝
        assertFalse(manager.acquire(apiKey, limit));
        assertEquals(3, manager.getActiveCount(apiKey));

        // 释放 1 个槽位 -> 重新可以获取
        manager.release(apiKey);
        assertEquals(2, manager.getActiveCount(apiKey));
        assertTrue(manager.acquire(apiKey, limit));
        assertEquals(3, manager.getActiveCount(apiKey));
    }

    @Test
    public void testMultiThreadConcurrency() throws InterruptedException {
        ConcurrencyControlManager manager = new ConcurrencyControlManager();
        String apiKey = "sk-multi-thread";
        int limit = 10;
        int threads = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    if (manager.acquire(apiKey, limit)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(10, successCount.get());
        assertEquals(10, manager.getActiveCount(apiKey));
    }
}
