package Components;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommandHandler {

    @Autowired
    public RespSerializer respSerializer;

    @Autowired
    public Store store;

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
            System.out.println(e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String[] command) {
        try {
            String key = command[1];
            return store.get(key);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "$-1\r\n";
        }
    }

}
