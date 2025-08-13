package Components.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import Components.Service.CommandHandler;
import Components.Service.RespSerializer;
import Components.Service.ResponseDto;

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

    @Autowired
    private Store store;

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

    private void handleCommand(String[] command, Client client) throws IOException {
        if (!client.getTransactionalContext()) {
            ResponseDto responseDto = caseHandler(command, client);
            client.send(responseDto);
        } else if (!isTransactionalControlCommand(command[0])) {
            // we are in the txnal context and the command is anormal cmd
            addCommandToTransaction(command, client);
        } else {
            // we are in the txnal context and the txnal context command is anormal cmd, ie,
            // EXEC or DISCARD
            transactionController(command, client);
        }

    }

    private void transactionController(String[] command, Client client) throws IOException {
        // control only comes here when we are in a txnal context
        switch (command[0]) {
            case "EXEC":
                if (client.commandQueue == null || client.commandQueue.isEmpty()) {
                    client.send("*0\r\n");
                    client.endTransaction();
                    return;
                }
                Queue<String[]> commands = new LinkedList<>(client.commandQueue);

                // execute a txn
                BiFunction<String[], Map<String, Value>, String> transactionCacheApplier = commandHandler
                        .getTransactionCommandCacheApplier();

                store.executeTransaction(client, transactionCacheApplier);

                client.endTransaction();
                while (!commands.isEmpty()) {
                    // String respArr = respSerializer.respArray(command);
                    // byte[] bytes = respArr.getBytes();
                    // connectionPool.bytesSentToSlaves += bytes.length;
                    // // trickle down to slaves
                    // CompletableFuture.runAsync(() -> propagate(command));

                    String[] commandToPropagate = commands.poll();
                    String commandRespString = respSerializer.respArray(commandToPropagate);
                    byte[] toCount = commandRespString.getBytes();
                    connectionPool.bytesSentToSlaves += toCount.length;
                    // trickle down to slaves
                    CompletableFuture.runAsync(() -> propagate(commandToPropagate));

                }

                String response = respSerializer.respArray(client.transactionResponse);

                client.send(response);

                break;
            case "DISCARD":
                client.endTransaction();
                client.send("+OK\r\n");
                break;
        }
    }

    private void addCommandToTransaction(String[] command, Client client) throws IOException {
        client.commandQueue.offer(command);
        client.send("+QUEUED\r\n");
    }

    private boolean isTransactionalControlCommand(String command) {
        return switch (command) {
            case "EXEC", "DISCARD" -> true;
            default -> false;
        };
    }

    public ResponseDto caseHandler(String[] command, Client client) {
        // control comes here only when the client is not in a txn
        String res = "";
        byte[] data = null;
        switch (command[0]) {
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "EXEC":
                res = "-ERR EXEC without MULTI\r\n";
                break;
            case "DISCARD":
                res = "-ERR DISCARD without MULTI\r\n";
                break;
            case "MULTI":
                client.beginTransaction();
                res = "+OK\r\n";
                break;
            case "INCR":
                res = commandHandler.incr(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "SET":
                res = commandHandler.set(command);
                String commandRespString = respSerializer.respArray(command);
                byte[] toCount = commandRespString.getBytes();
                connectionPool.bytesSentToSlaves += toCount.length;
                // trickle down to slaves
                CompletableFuture.runAsync(() -> propagate(command));
                // String respArr = respSerializer.respArray(command);
                // byte[] bytes = respArr.getBytes();
                // connectionPool.bytesSentToSlaves += bytes.length;
                // // trickle down to slaves
                // CompletableFuture.runAsync(() -> propagate(command));
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
            case "WAIT":
                if (connectionPool.bytesSentToSlaves == 0) {
                    res = respSerializer.respInteger(connectionPool.slavesThatAreCaughtUp);
                    break;
                }
                Instant start = Instant.now();
                res = commandHandler.wait(command, start);
                connectionPool.slavesThatAreCaughtUp = 0;
                break;
            case "PSYNC":
                ResponseDto resDto = commandHandler.psync(command);
                res = resDto.response;
                data = resDto.data;
                break;
        }
        return new ResponseDto(res, data);
    }

    private void propagate(String[] command) {
        String commandRespString = respSerializer.respArray(command);
        try {
            for (Slave slave : connectionPool.getSlaves()) {
                System.out.println("++++++++++++++++++ sending cmd down to slave ++++++++++++++++++++++++++++++");
                System.out.println("coammand: " + commandRespString);
                System.out.println(slave.connection.id);
                InetAddress remoteAddress = slave.connection.socket.getInetAddress();
                System.out.println("Remote IP address: " + remoteAddress.getHostAddress() + ": "
                        + slave.connection.socket.getPort());

                slave.send(commandRespString.getBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
