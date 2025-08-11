package Components.Infra;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Slave {
    public Client connection;
    public List<String> capabilities;

    public Slave(Client client) {
        this.connection = client;
        this.capabilities = new ArrayList<>();
    }

    public void send(byte[] bytes) throws IOException {
        if (bytes != null) {
            this.connection.outputStream.write(bytes);
        }
    }
}
