package org.scalasbt.ipcsocket;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.Random;

public class UnixDomainSocketTest {
  @Test
  public void testAssertEquals() throws IOException, InterruptedException {
    Random rand = new Random();
    Path tempDir = Files.createTempDirectory("ipcsocket");
    Path sock = tempDir.resolve("foo" + rand.nextInt() + ".sock");
    ServerSocket serverSocket = new UnixDomainServerSocket(sock.toString());

    CompletableFuture<Boolean> server = CompletableFuture.supplyAsync(() -> {
      try {
        EchoServer echo = new EchoServer(serverSocket);
        echo.run();
      } catch (IOException e) { }
      return true;
    });
    Thread.sleep(100);

    Socket client = new UnixDomainSocket(sock.toString());
    PrintWriter out =
      new PrintWriter(client.getOutputStream(), true);
    BufferedReader in = new BufferedReader(
      new InputStreamReader(client.getInputStream()));
    out.println("hello");
    String line = in.readLine();
    client.close();
    server.cancel(true);
    serverSocket.close();
    assertEquals("echo did not return the content", line, "hello");
  }
}
