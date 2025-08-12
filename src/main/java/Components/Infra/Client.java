package Components.Infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import Components.Service.ResponseDto;

public class Client {
    public Socket socket;
    public InputStream inputStream;
    public OutputStream outputStream;
    public int id;

    private boolean transactionalContext;
    public Queue<String[]> commandQueue;
    public List<String> transactionResponse; // the strings will be in RESP formt, we just need to make a RESP array out
                                             // of it

    public boolean getTransactionalContext() {
        return transactionalContext;
    }

    public boolean beginTransaction() {
        if (transactionalContext)
            return false;
        transactionalContext = true;

        transactionResponse = new ArrayList<>();
        commandQueue = new LinkedList<>();

        return transactionalContext;
    };

    public void endTransaction() {
        commandQueue = null;
        transactionalContext = false;
    }

    public Client(Socket socket, InputStream inputStream, OutputStream outputStream, int id) {
        this.socket = socket;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.id = id;
    }

    public void send(String res, byte[] data) throws IOException {
        if (res != null && !res.equals(""))
            outputStream.write(res.getBytes());

        if (data != null) {
            outputStream.write(data);
        }
    }

    public void send(ResponseDto res) throws IOException {
        if (res.response != null && !res.response.isEmpty()) {
            outputStream.write(res.response.getBytes());
        }
        if (res.data != null) {
            outputStream.write(res.data);
        }
    }

    public void send(byte[] data) throws IOException {
        if (data != null) {
            outputStream.write(data);
        }
    }

    public void send(String data) throws IOException {
        if (data != null && !data.isEmpty()) {
            outputStream.write(data.getBytes());
        }
    }
}
