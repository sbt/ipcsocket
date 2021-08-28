package org.scalasbt.ipcsocket.duplex;

import java.io.IOException;
import java.util.concurrent.Executors;
import org.scalasbt.ipcsocket.BaseSocketSetup;

public class DuplexServer extends BaseSocketSetup {

    private final String pipeName;
    private final int sendMessages;

    public Sender sender;
    public Receiver receiver;

    public DuplexServer(String pipeName, int sendMessages) {
        this.pipeName = pipeName;
        this.sendMessages = sendMessages;
    }

    public void startAndAwait() {
        var pool = Executors.newFixedThreadPool(10);

        System.out.println("DuplexServer started. Waiting for client...");
        try (var serverSocket = newServerSocket(pipeName);
                var socket = serverSocket.accept()) {

            sender = new Sender("server", socket, sendMessages);
            receiver = new Receiver("server", socket);
            pool.execute(sender);
            pool.execute(receiver);

            Thread.sleep((sendMessages + 2) * 1000);
            pool.shutdownNow();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

}
