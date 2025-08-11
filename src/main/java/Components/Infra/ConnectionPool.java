package Components.Infra;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ConnectionPool {
    private Set<Client> clients;
    private Set<Slave> slaves;

    public ConnectionPool() {
        clients = new HashSet<>();
        slaves = new HashSet<>();
    }

    public Set<Client> getClients() {
        return clients;
    }

    public Set<Slave> getSlaves() {
        return slaves;
    }

    public void addClient(Client client) {
        if (client != null) {
            clients.add(client);
        }
    }

    public void addSlave(Slave slave) {
        if (slave != null) {
            slaves.add(slave);
        }
    }

    public boolean removeClient(Client client) {
        return clients.remove(client);
    }

    public boolean removeSlave(Slave slave) {
        return slaves.remove(slave);
    }

    public boolean removeSlave(Client client) {
        Slave slaveToRemove = null;
        for (Slave s : slaves) {
            if (s.connection.equals(client)) {
                slaveToRemove = s;
                break;
            }
        }
        return slaves.remove(slaveToRemove);
    }
}
