package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Streams data through a socket while the JVM is busy with safepoints: a JFR recording, a thread
 * spawning processes, and a busy thread per core. A read or write that succeeds must not be
 * reported as failing: errno can hold a stale value from whatever ran after the syscall (on macOS,
 * a JNI call that blocks for a safepoint leaves psynch bits such as 260 in it).
 */
public class StreamUnderLoadTest extends BaseSocketSetup {
  @Test
  public void streamUnderLoad() throws Exception {
    if (isWin) return;
    try (Recording recording = new Recording(Configuration.getConfiguration("profile"))) {
      recording.start();
      streamThroughSocket();
    }
  }

  private void streamThroughSocket() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          AtomicBoolean done = new AtomicBoolean(false);
          Thread spawner =
              new Thread(
                  () -> {
                    while (!done.get()) {
                      try {
                        new ProcessBuilder("true").start().waitFor();
                      } catch (IOException | InterruptedException e) {
                        return;
                      }
                    }
                  });
          spawner.setDaemon(true);
          spawner.start();
          // busy threads make safepoints slower to reach, so JNI calls block for longer
          for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            Thread burner =
                new Thread(
                    () -> {
                      while (!done.get()) Thread.onSpinWait();
                    });
            burner.setDaemon(true);
            burner.start();
          }
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
          CompletableFuture<Long> written =
              CompletableFuture.supplyAsync(
                  () -> {
                    try (Socket server = serverSocket.accept()) {
                      OutputStream out = server.getOutputStream();
                      byte[] chunk = new byte[64 * 1024];
                      long n = 0;
                      while (System.nanoTime() < deadline) {
                        out.write(chunk);
                        n += chunk.length;
                      }
                      return n;
                    } catch (IOException e) {
                      throw new RuntimeException("write failed", e);
                    }
                  });
          long read = 0;
          // reading to EOF closes the socket, so it is not closed again here
          Socket client = newClientSocket(sock);
          try {
            InputStream in = client.getInputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) >= 0) {
              // a slow reader keeps the writer blocked, as a client printing to a terminal does
              if ((read & 0xFFFFF) + r > 0xFFFFF) Thread.sleep(50);
              read += r;
            }
          } finally {
            done.set(true);
            serverSocket.close();
          }
          long expected;
          try {
            expected = written.get(30, TimeUnit.SECONDS);
          } catch (Exception e) {
            throw new AssertionError("writer failed", e);
          }
          assertEquals("bytes read", expected, read);
        });
  }
}
