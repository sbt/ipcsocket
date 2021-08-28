package org.scalasbt.ipcsocket.duplex;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class Sender implements Runnable {

    private final String name;
    private final Socket socket;
    private final int sendMessages;
    private final Random random = new Random();

    public Sender(String name, Socket socket, int sendMessages) {
        this.name = name;
        this.socket = socket;
        this.sendMessages = sendMessages;
    }

    @Override
    public void run() {
        try (var out = new PrintWriter(socket.getOutputStream(), true)) {
            for (int i = 0; i < sendMessages; i++) {
                System.out.println("[" + name + "] sending msg: " + i);
                out.println("hello" + i);
                Thread.sleep(Math.abs(random.nextInt(1000)));
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
