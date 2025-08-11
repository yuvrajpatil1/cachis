package Components.Infra;

import java.util.ArrayList;
import java.util.List;

public class Slave {
    public Client connection;
    public List<String> capabilities;

    public Slave(Client client) {
        this.connection = client;
        this.capabilities = new ArrayList<>();
    }
}
