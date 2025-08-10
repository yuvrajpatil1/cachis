package Components.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

@Component
public class RespSerializerTest {
    private final RespSerializer respSerializer = new RespSerializer();

    @Test
    public void testDeserialzePing() {
        String ping = "*1\r\n$4\r\nPING\r\n";
        List<String[]> commands = respSerializer.deserialize(ping.getBytes(StandardCharsets.UTF_8));

        for (String[] s : commands) {
            for (String ss : s) {
                System.out.print(ss + " ");
            }
        }
        assertEquals(1, commands.size());
        assertEquals(1, commands.get(0).length);
        assertEquals("PING", commands.get(0)[0]);
    }

    @Test
    public void testMultipleCommands() {
        // "\u0000"
        String multipleCommands = "*2\r\n*3\r\n$3\r\nset\r\n$3\r\nkey\r\n$5\r\nvalue\r\n*3\r\n$3\r\nset\r\n$3\r\nkey\r\n$5\r\nvalue\u0000";
        List<String[]> commands = respSerializer.deserialize(multipleCommands.getBytes(StandardCharsets.UTF_8));
        System.out.println(commands.size());

        for (String[] s : commands) {
            for (String ss : s) {
                System.out.print(ss + " ");
            }
        }
        assertEquals(2, commands.size());
        assertEquals(3, commands.get(0).length);
        assertEquals(3, commands.get(1).length);

        assertEquals("set", commands.get(0)[0]);
        assertEquals("key", commands.get(0)[1]);
        assertEquals("value", commands.get(0)[3]);

        assertEquals("set", commands.get(1)[0]);
        assertEquals("key", commands.get(1)[1]);
        assertEquals("value", commands.get(1)[3]);
    }
}
