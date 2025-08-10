package Components.Repository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import Components.Service.CommandHandler;
import Components.Service.RespSerializer;

@Component
public class Store {
    private static final Logger logger = Logger.getLogger(Store.class.getName());

    public ConcurrentHashMap<String, Value> map;

    @Autowired
    public RespSerializer respSerializer;

    public Store() {
        map = new ConcurrentHashMap<>();
    }

    public Set<String> getKeys() {
        return map.keySet();
    }

    public String set(String key, String val) {
        try {
            Value value = new Value(val, LocalDateTime.now(), LocalDateTime.MAX);
            map.put(key, value);
            return "+OK\r\n";
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        }
    }

    public String set(String key, String val, int expiryMilliseconds) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime exp = now.plus(expiryMilliseconds, ChronoUnit.MILLIS);
            Value value = new Value(val, now, exp);
            map.put(key, value);
            return "+OK\r\n";
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String key) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Value value = map.get(key);

            if (value.expiry.isBefore(now)) {
                map.remove(key);
                return "$-1\r\n";
            }
            return respSerializer.serializeBulkString(value.val);
        } catch (

        Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        }
    }
}
