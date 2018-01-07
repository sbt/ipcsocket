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

public class Win32NamedPipeSocketTest {
  @Test
  public void testAssertEquals() throws IOException, InterruptedException {
    String pipeName = "\\\\.\\pipe\\ipcsockettest";
    CompletableFuture<Boolean> server = CompletableFuture.supplyAsync(() -> {
      try {
        ServerSocket serverSocket = new Win32NamedPipeServerSocket(pipeName);
        EchoServer echo = new EchoServer(serverSocket);
        echo.run();
      } catch (IOException e) { }
      return true;
    });
    Thread.sleep(100);

    Socket client = new Win32NamedPipeSocket(pipeName);
    PrintWriter out =
      new PrintWriter(client.getOutputStream(), true);
    BufferedReader in = new BufferedReader(
      new InputStreamReader(client.getInputStream()));
    out.println("hello");
    String line = in.readLine();
    System.out.println("windows client: " + line);
    client.close();
    server.cancel(true);
    assertEquals("echo did not return the content", line, "hello");
  }
}
