package Components.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import Components.Infra.Client;
import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Repository.Store;
import Components.Repository.Value;
import Components.Server.RedisConfig;

@Component
public class CommandHandler {

    public static final String emptyRdbFile = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";

    private static final Logger logger = Logger.getLogger(CommandHandler.class.getName());

    @Autowired
    public RespSerializer respSerializer;

    @Autowired
    public Store store;

    @Autowired
    public RedisConfig redisConfig;

    @Autowired
    public ConnectionPool connectionPool;

    public String ping(String[] command) {
        return "+PONG\r\n";
    }

    public String echo(String[] command) {
        return respSerializer.serializeBulkString(command[1]);
    }

    public String set(String[] command) {
        try {
            // todo: global exception handling
            String key = command[1];
            String value = command[2];

            int pxFlag = Arrays.stream(command).toList().indexOf("px");
            // -1

            if (pxFlag > -1) {
                int delta = Integer.parseInt(command[pxFlag + 1]);
                return store.set(key, value, delta);
            } else {
                return store.set(key, value);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String[] command) {
        try {
            String key = command[1];
            return store.get(key);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
            return "$-1\r\n";
        }
    }

    public String info(String[] command) {
        // command[0]; info

        int replication = Arrays.stream(command).toList().indexOf("replication");
        if (replication > -1) {
            String role = "role:" + redisConfig.getRole();
            String masterReplId = "master_replid:" + redisConfig.getMasterReplId();
            String masterReplOffset = "master_repl_offset:" + redisConfig.getMasterReplOffset();

            String[] info = new String[] { role, masterReplId, masterReplOffset };
            String replicationData = String.join("\r\n", info);

            return respSerializer.serializeBulkString(replicationData);
        }

        return "";
    }

    public String replconf(String[] command, Client client) {

        switch (command[1]) {
            case "GETACK":
                String[] replConfAck = new String[] { "REPLCONF", "ACK", redisConfig.getMasterReplOffset() + "" };
                return respSerializer.respArray(replConfAck);

            case "ACK":
                int ackResponse = Integer.parseInt(command[2]);
                connectionPool.slaveAck(ackResponse);
                return "";

            case "listening-port":
                connectionPool.removeClient(client);
                Slave s = new Slave(client);
                connectionPool.addSlave(s);
                return "+OK\r\n";

            case "capa":
                Slave slave = null;
                for (Slave ss : connectionPool.getSlaves()) {
                    if (ss.connection.equals(client)) {
                        slave = ss;
                        break;
                    }
                }
                for (int i = 0; i < command.length; i++) {
                    if (command[i].equals("capa")) {
                        slave.capabilities.add(command[i + 1]);
                    }
                }
                return "+OK\r\n";
        }
        return "+OK\r\n";
    }

    public byte[] concatenate(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);

        return result;
    }

    public ResponseDto psync(String[] command) {
        String replicationIdMaster = command[1];
        String replicationOffsetMaster = command[2];

        if (replicationIdMaster.equals("?") && replicationOffsetMaster.equals("-1")) {
            String replicationId = redisConfig.getMasterReplId();
            long replicationOffset = redisConfig.getMasterReplOffset();
            String res = "+FULLRESYNC " + replicationId + " " + replicationOffset + "\r\n";

            byte[] rdbFileData = Base64.getDecoder().decode(emptyRdbFile);

            String length = rdbFileData.length + "";

            String fullReSyncHeader = "$" + length + "\r\n";

            byte[] header = fullReSyncHeader.getBytes();

            connectionPool.slavesThatAreCaughtUp++;

            return new ResponseDto(res, concatenate(header, rdbFileData));
        } else {
            return new ResponseDto("Options not supported yet.");
        }
    }

    public String wait(String[] command, Instant now) {
        String[] getAckArr = new String[] { "REPLCONF", "GETACK", "*" };
        String getAck = respSerializer.respArray(getAckArr);
        byte[] bytes = getAck.getBytes();
        int bufferSize = bytes.length;

        int required = Integer.parseInt(command[1]); // no of slaves ww are required to wait for
        int time = Integer.parseInt(command[2]);

        for (Slave slave : connectionPool.getSlaves()) {
            CompletableFuture.runAsync(() -> {
                try {
                    slave.connection.send(bytes);
                } catch (IOException e) {
                    throw new RuntimeException();
                }
            });
        }

        int res = 0;
        while (true) {
            if (Duration.between(now, Instant.now()).toMillis() >= time) {
                break;
            }
            if (res >= required) {
                break;
            }
            res = connectionPool.slavesThatAreCaughtUp;
        }
        connectionPool.bytesSentToSlaves += bufferSize;
        if (res >= required) {
            return respSerializer.respInteger(required);
        }
        return respSerializer.respInteger(res);
    }

    public String incr(String[] command) {
        String key = command[1];
        String res = "";

        try {
            Value value = store.getValue(key);
            if (value == null) {
                store.set(key, "0");
                value = store.getValue(key);
            }

            int val = Integer.parseInt(value.val);
            val++;
            value.val = val + "";

            res = respSerializer.respInteger(val);

        } catch (Exception e) {
            res = "-ERR value is not an integer or out of range\r\n";
        }

        return res;
    }

    public BiFunction<String[], Map<String, Value>, String> getTransactionCommandCacheApplier() {
        final Store localStore = this.store;
        final RespSerializer localSerializer = this.respSerializer;
        return (String[] command, Map<String, Value> map) -> {
            String res = "";
            switch (command[0]) {
                case "SET":
                    res = handleSetCoammandTransactional(command, map, localSerializer, localStore);
                    break;
                case "GET":
                    res = handleGetCoammandTransactional(command, map, localSerializer, localStore);
                    break;
                case "INCR":
                    res = handleIncrCoammandTransactional(command, map, localSerializer, localStore);
                    break;
                case "DEL":

                    res = handleDelCoammandTransactional(command, map, localSerializer, localStore);
                    break;
                default:
                    res = "-ERR unknown command " + command[0] + "\r\n";
                    break;
            }
            return res;
        };
    }

    private String handleDelCoammandTransactional(String[] command, Map<String, Value> map,
            RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value valueToUse;
        Value cachedValue = map.getOrDefault(key, null);
        if (cachedValue == null) {
            Value storeValue = localStore.getValue(key);
            if (storeValue == null) {
                // both are null
                return "-ERR deleting invalid key\r\n";
            } else {
                valueToUse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
            }
        } else {
            valueToUse = cachedValue;
        }
        valueToUse.isDeletedInTransaction = true;
        map.put(key, valueToUse);
        return "+OK\r\n";

    }

    private String handleSetCoammandTransactional(String[] command, Map<String, Value> map,
            RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value newValue = new Value(command[2], LocalDateTime.now(), LocalDateTime.MAX);
        map.put(key, newValue);
        return "+OK\r\n";
    }

    private String handleGetCoammandTransactional(String[] command, Map<String, Value> map,
            RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value valueToUse;
        Value cachedValue = map.getOrDefault(key, null);
        if (cachedValue == null) {
            Value storeValue = localStore.getValue(key);
            if (storeValue == null) {
                // both are null
                return localStore.get(key);
            } else {
                valueToUse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
                map.put(key, valueToUse);
            }
        } else {
            valueToUse = cachedValue;
        }
        return respSerializer.serializeBulkString(valueToUse.val);
    }

    private String handleIncrCoammandTransactional(String[] command, Map<String, Value> map,
            RespSerializer localSerializer, Store localStore) {
        try {
            String key = command[1];
            Value valueToUse;
            Value cachedValue = map.getOrDefault(key, null);
            if (cachedValue == null) {
                Value storeValue = localStore.getValue(key);
                if (storeValue == null) {
                    valueToUse = new Value("0", LocalDateTime.now(), LocalDateTime.MAX);
                } else {
                    valueToUse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
                }
            } else {
                valueToUse = cachedValue;
            }
            int val = Integer.parseInt(valueToUse.val);
            val++;
            valueToUse.val = String.valueOf(val);
            map.put(key, valueToUse);
            return respSerializer.respInteger(val);
        } catch (Exception e) {
            return "-ERR value is not an integer or out of the range.";
        }

    }
}
