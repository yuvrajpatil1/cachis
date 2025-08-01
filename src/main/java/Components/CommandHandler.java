package Components;

import org.springframework.stereotype.Component;

@Component
public class CommandHandler {
    public String ping(String[] command) {
        return "+PONG\r\n";
    }

    public String echo(String[] command) {
        int length = command[1].length();
        String respHeader = "$" + length;
        String respBody = command[1];
        return respHeader + "\r\n" + respBody + "\r\n";
    }

    public String set(String[] command) {
        return "+PONG\r\n";
    }

}
