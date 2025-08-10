package Components.Repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import Components.Service.RespSerializer;
import Config.AppConfig;

@SpringBootTest(classes = AppConfig.class)
public class StoreTest {
    @Autowired
    private Store store;
    private RespSerializer respSerializer;

    @BeforeEach
    public void setUp() {
        store.map.clear();
    }

    @Test
    public void testSetAndGetKey() {
        String key = "testKey";
        String value = "testValue";

        String setResult = store.set(key, value);
        String getResult = store.get(key);

        assertEquals("+OK\r\n", setResult);
        assertEquals(respSerializer.serializeBulkString(value), getResult);
    }

    @Test
    public void testSetAndGetKeyExpiry() throws InterruptedException {
        String key = "testKey";
        String value = "testValue";
        int expiryMilliseconds = 100;

        String setResult = store.set(key, value, expiryMilliseconds);
        String getResult = store.get(key);

        Thread.sleep((long) 100.0);

        String getResultExpiry = store.get(key);

        assertEquals("+OK\r\n", setResult);
        assertEquals(respSerializer.serializeBulkString(value), getResult);

        assertEquals("$-1\r\n", getResultExpiry);
    }

    @Test
    public void testSetAndGetKeyExpiryReset() throws InterruptedException {
        String key = "testKey";
        String value = "testValue";
        String value2 = "testValue2";
        int expiryMilliseconds = 100;

        String setResult = store.set(key, value, expiryMilliseconds);
        String getResult = store.get(key);
        String setResultReset = store.set(key, value2, expiryMilliseconds * 5);

        Thread.sleep((long) 100.0);
        String getResultReset = store.get(key);

        assertEquals("+OK\r\n", setResult);
        assertEquals("+OK\r\n", setResultReset);
        assertEquals(respSerializer.serializeBulkString(value), getResult);

        assertEquals(respSerializer.serializeBulkString(value2), getResultReset);
    }

    @Test
    public void testConcurrentlySetting() throws InterruptedException {
        List<CompletableFuture<Void>> l = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 100; j++) {
                    store.set("key" + j + "," + finalI, "ooga booga");
                }
            });
            l.add(future);
        }
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(l.toArray(new CompletableFuture[l.size()]));
        allFutures.join();

        assertEquals(1000, store.getKeys().size());
    }
}
