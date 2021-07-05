package ipcsocketexample;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.scalasbt.ipcsocket.*;

public class ExampleApp 
{
    static final boolean isWin = System.getProperty("os.name", "").toLowerCase().startsWith("win");

    static ServerSocket newServerSocket(String socketName) throws IOException {
        return isWin
            ? new Win32NamedPipeServerSocket(socketName, false, Win32SecurityLevel.LOGON_DACL)
            : new UnixDomainServerSocket(socketName, false);
    }

    static Socket newClientSocket(String socketName) throws IOException {
        return isWin
            ? new Win32NamedPipeSocket(socketName, false)
            : new UnixDomainSocket(socketName, false);
    }

    public static void main( String[] args )
    {
        Path socketPath = null;
        Path tempDir = null;

        try {
            tempDir = isWin ? null : Files.createTempDirectory("ipcsocket");
            socketPath = tempDir != null ? tempDir.resolve("/tmp/socket-loc.sock") : null;
            String socketName =
                socketPath != null ? socketPath.toString() : "\\\\.\\pipe\\ipcsockettest";
            ServerSocket serverSocket = newServerSocket(socketName);

            EchoServer echo = new EchoServer(serverSocket);
            echo.run();
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            try {
                if (socketPath != null) Files.deleteIfExists(socketPath);
                if (tempDir != null) Files.deleteIfExists(socketPath);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }
}
