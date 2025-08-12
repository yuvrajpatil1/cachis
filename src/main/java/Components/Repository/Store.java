package Components.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import Components.Infra.Client;
import Components.Service.RespSerializer;

@Component
public class Store {
    private static final Logger logger = Logger.getLogger(Store.class.getName());
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    public ConcurrentHashMap<String, Value> map;

    @Autowired
    public RespSerializer respSerializer;

    public Store() {
        map = new ConcurrentHashMap<>();
    }

    public Set<String> getKeys() {
        rwLock.readLock().lock();
        try {
            return map.keySet();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String set(String key, String val) {
        rwLock.writeLock().lock();
        try {
            Value value = new Value(val, LocalDateTime.now(), LocalDateTime.MAX);
            map.put(key, value);
            return "+OK\r\n";
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String set(String key, String val, int expiryMilliseconds) {
        rwLock.writeLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime exp = now.plus(expiryMilliseconds, ChronoUnit.MILLIS);
            Value value = new Value(val, now, exp);
            map.put(key, value);
            return "+OK\r\n";
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String get(String key) {
        rwLock.readLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            Value value = map.get(key);

            if (value != null && value.expiry.isBefore(now)) {
                map.remove(key);
                return "$-1\r\n";
            }
            return respSerializer.serializeBulkString(value.val);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Value getValue(String key) {
        rwLock.readLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            Value value = map.getOrDefault(key, null);

            if (value != null && value.expiry.isBefore(now)) {
                map.remove(key);
                return null;
            }
            return value;
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void executeTransaction(Client client,
            BiFunction<String[], Map<String, Value>, String> transactionCacheApplier) {
        rwLock.writeLock().lock();
        Map<String, Value> localCache = new HashMap<>();
        List<String> responses = new ArrayList<>();
        try {
            while (!client.commandQueue.isEmpty()) {
                String[] command = client.commandQueue.poll();
                String response = transactionCacheApplier.apply(command, localCache);
                responses.add(response);
            }
            // control will come here when the queue is empty, thahat means no other
            // commands in the txs]n left to be applied
            for (Map.Entry<String, Value> entry : localCache.entrySet()) {
                String key = entry.getKey();
                Value value = entry.getValue();

                if (value.isDeletedInTransaction) {
                    this.map.remove(key);
                } else {
                    this.map.put(key, value);
                }
            }
            client.transactionResponse.addAll(responses);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
