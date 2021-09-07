package org.scalasbt.ipcsocket.duplex;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.scalasbt.ipcsocket.BaseSocketSetup;

public class DuplexClient extends BaseSocketSetup {

  private final String pipeName;
  private final int sendMessages;

  public Sender sender;
  public Receiver receiver;

  public DuplexClient(String pipeName, int sendMessages) {
    this.pipeName = pipeName;
    this.sendMessages = sendMessages;
  }

  public void startAndAwait() {
    ExecutorService pool = Executors.newFixedThreadPool(2);

    try (Socket socket = newClientSocket(pipeName)) {
      sender = new Sender("client", socket, sendMessages);
      receiver = new Receiver("client", socket);
      pool.execute(sender);
      pool.execute(receiver);

      Thread.sleep(sendMessages * 1000);
      pool.shutdownNow();
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    }
  }
}
