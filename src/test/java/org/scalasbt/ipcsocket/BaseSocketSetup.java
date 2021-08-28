package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public abstract class BaseSocketSetup {

  final boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");
  
  Random rand = new Random();

  boolean useJNI() {
    return false;
  }

  public static interface MayThrow {
    void accept(String string) throws IOException, InterruptedException;
  }
  
  protected void withSocket(final MayThrow consumer) throws IOException, InterruptedException {
    Path tempDir = isWin ? null : Files.createTempDirectory("ipcsocket");
    Path socketPath = tempDir != null ? tempDir.resolve("foo" + rand.nextInt() + ".sock") : null;
    String socket =
        socketPath != null ? socketPath.toString() : "\\\\.\\pipe\\ipcsockettest" + rand.nextInt();
    try {
      consumer.accept(socket);
    } finally {
      if (socketPath != null) Files.deleteIfExists(socketPath);
      if (tempDir != null) Files.deleteIfExists(socketPath);
    }
  }

  protected ServerSocket newServerSocket(String socketName) throws IOException {
    return isWin
        ? new Win32NamedPipeServerSocket(socketName, useJNI(), Win32SecurityLevel.LOGON_DACL)
        : new UnixDomainServerSocket(socketName, useJNI());
  }

  protected Socket newClientSocket(String socketName) throws IOException {
    return isWin
        ? new Win32NamedPipeSocket(socketName, useJNI())
        : new UnixDomainSocket(socketName, useJNI());
  }
}
