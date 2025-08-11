package Components.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import Components.Infra.Client;
import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Service.CommandHandler;
import Components.Service.RespSerializer;

@Component
public class MasterTcpServer {
    private static final Logger logger = Logger.getLogger(MasterTcpServer.class.getName());

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    private ConnectionPool connectionPool;

    public void startServer() {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = redisConfig.getPort();
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            int id = 0;

            while (true) {
                clientSocket = serverSocket.accept();
                id++;
                Socket finalClientSocket = clientSocket;

                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                Client client = new Client(finalClientSocket, inputStream, outputStream, id);
                CompletableFuture.runAsync(() -> {
                    try {
                        handleClient(client);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "IOException: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "IOException: " + e.getMessage());
            }
        }
    }

    private void handleClient(Client client) throws IOException {
        connectionPool.addClient(client);
        while (client.socket.isConnected()) {
            byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
            int bytesRead = client.inputStream.read(buffer);
            if (bytesRead > 0) {
                // bytes parsing into strings
                List<String[]> commands = respSerializer.deserialize(buffer);
                for (String[] command : commands) {
                    handleCommand(command, client);
                }
            }
        }
        connectionPool.removeClient(client);
        connectionPool.removeSlave(client);
    }

    private void handleCommand(String[] command, Client client) {
        String res = "";
        switch (command[0]) {
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "SET":
                res = commandHandler.set(command);
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
            case "INFO":
                res = commandHandler.info(command);
                break;
            case "REPLCONF":
                res = commandHandler.replconf(command, client);
                break;
            case "PSYNC":
                res = commandHandler.psync(command);
                break;
        }

        if (res != null && !res.equals(""))
            try {
                client.outputStream.write(res.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
}
