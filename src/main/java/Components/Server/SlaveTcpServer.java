package Components.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import Components.Service.CommandHandler;
import Components.Service.RespSerializer;
import Infra.Client;

@Component
public class SlaveTcpServer {
    private static final Logger logger = Logger.getLogger(SlaveTcpServer.class.getName());

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    @Autowired
    private RedisConfig redisConfig;

    public void startServer() {
        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = redisConfig.getPort();

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);

            CompletableFuture<Void> slaveCompletableFuture = CompletableFuture.runAsync(this::initiateSlavery);
            slaveCompletableFuture.thenRun(() -> System.out.println("Replication completed"));

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

    private void initiateSlavery() {
        try (Socket master = new Socket(redisConfig.getMasterHost(), redisConfig.getMasterPort())) {
            InputStream inputStream = master.getInputStream();
            OutputStream outputStream = master.getOutputStream();
            byte[] inputBuffer = new byte[1024];

            // part 1 of the hanshake
            byte[] data = "*1\r\n$4\r\nPING\r\n".getBytes();
            outputStream.write(data);
            int bytesRead = inputStream.read(inputBuffer, 0, inputBuffer.length);

            String response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            // prt 2 of the hanshake
            int lenListeningPort = (redisConfig.getPort() + "").length();
            int listeningPort = redisConfig.getPort();
            String replconf = "*3\r\n$8\r\nREPLCONF\r\n$14\r\nlistening-port\r\n$" + (lenListeningPort + "") + "\r\n"
                    + (listeningPort + "") + "\r\n";
            data = replconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer, 0, inputBuffer.length);

            response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            replconf = "*3\r\n$8\r\nREPLCONF\r\n$4\r\ncapa\r\n$6\r\npsync2\r\n";
            data = replconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer, 0, inputBuffer.length);

            response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            // part 2 of the hanshake
            String psync = "*3\r\n$5\r\nPSYNC\r\n$1\r\n?\r\n$2\r\n-1\r\n";
            data = psync.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer, 0, inputBuffer.length);

            response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            logger.log(Level.FINE, response);

            // handlePsyncResponse(inputStream);

        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }

    private void handleClient(Client client) throws IOException {

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
                res = "-READONLY You can't write against a replica.\r\n";
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
            case "INFO":
                res = commandHandler.info(command);
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
