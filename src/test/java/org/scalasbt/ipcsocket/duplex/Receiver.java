package org.scalasbt.ipcsocket.duplex;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

public class Receiver implements Runnable {

    private final String name;
    private final Socket socket;
    public volatile int receivedMessages;
    private final Random random = new Random();

    public Receiver(String name, Socket socket) {
        this.name = name;
        this.socket = socket;
    }

    @Override
    public void run() {
        try (var in = new Scanner(socket.getInputStream())) {
            while (true) {
                while (in.hasNextLine()) {
                    System.out.println("[" + name + "] got a message: " + in.nextLine());
                    receivedMessages++;
                }
                Thread.sleep(Math.abs(random.nextInt(1000)));
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
