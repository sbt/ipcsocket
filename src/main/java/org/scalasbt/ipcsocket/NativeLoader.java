package org.scalasbt.ipcsocket;

import java.util.concurrent.atomic.AtomicBoolean;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.FileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

class NativeLoader {
  private static final AtomicBoolean loaded = new AtomicBoolean(false);
  private static final boolean isMac;
  private static final boolean isLinux;
  private static final boolean isWindows;

  static {
    final String os = System.getProperty("os.name", "").toLowerCase();
    isMac = os.startsWith("mac");
    isLinux = os.startsWith("linux");
    isWindows = os.startsWith("windows");
  }

  private static final String pid =
      isWindows ? "" : ManagementFactory.getRuntimeMXBean().getName().replaceAll("@.*", "");

  private static Path runtimeDir;

  /**
   * The directory to extract the library to. Without XDG_RUNTIME_DIR, the default has a predictable
   * name in a shared temp directory. If another user created it first, they could swap the library
   * before it is loaded, so then use a fresh private directory instead.
   */
  private static synchronized Path runtimeDir() throws IOException {
    if (runtimeDir == null) {
      String prop = System.getProperty("sbt.ipcsocket.tmpdir");
      if (prop != null) {
        runtimeDir = Files.createDirectories(Paths.get(prop));
      } else {
        String tmp = System.getenv("XDG_RUNTIME_DIR");
        if (tmp == null) {
          tmp = System.getProperty("java.io.tmpdir");
        }
        String home = System.getProperty("user.home");
        if (home == null) {
          home = "unknown_home";
        }
        final Path sbtDir = Paths.get(tmp).resolve(".sbt" + Integer.toString(home.hashCode()));
        final Path dir = sbtDir.resolve("ipcsocket");
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
          runtimeDir = Files.createDirectories(dir);
        } else {
          Files.createDirectories(sbtDir.getParent());
          if (isPrivateDirectory(sbtDir) && isPrivateDirectory(dir)) {
            runtimeDir = dir;
          } else {
            runtimeDir = Files.createTempDirectory(tempFilePrefix);
            runtimeDir.toFile().deleteOnExit();
          }
        }
      }
    }
    return runtimeDir;
  }

  /** Creates dir if needed, and checks that only this user can change what is in it. */
  static boolean isPrivateDirectory(Path dir) throws IOException {
    try {
      Files.createDirectory(
          dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    } catch (final FileAlreadyExistsException e) {
    }
    final PosixFileAttributes attrs =
        Files.readAttributes(dir, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    final Set<PosixFilePermission> perms = attrs.permissions();
    return attrs.isDirectory()
        && attrs.owner().getName().equals(System.getProperty("user.name"))
        && !perms.contains(PosixFilePermission.GROUP_WRITE)
        && !perms.contains(PosixFilePermission.OTHERS_WRITE);
  }

  private static final String tempFilePrefix = "libsbtipcsocket";

  static void load() throws UnsatisfiedLinkError {
    if (!loaded.get()) {
      final String os = System.getProperty("os.name", "").toLowerCase();
      final boolean isMac = os.startsWith("mac");
      final boolean isLinux = os.startsWith("linux");
      final boolean isWindows = os.startsWith("windows");
      final boolean is64bit = System.getProperty("sun.arch.data.model", "64").equals("64");
      String arch = System.getProperty("os.arch", "").toLowerCase();
      if (arch.equals("amd64")) {
        arch = "x86_64";
      }
      // https://github.com/sbt/sbt/issues/7117
      // Currently only Linux has ARM-specific binary. The macOS binary is a universal binary,
      // and Windows can potentially emulate x86.
      if (isMac || isWindows) {
        arch = "x86_64";
      }
      if (is64bit && (isMac || isLinux || isWindows)) {
        final String extension = "." + (isMac ? "dylib" : isWindows ? "dll" : "so");
        final String libName = (isWindows ? "" : "lib") + "sbtipcsocket" + extension;
        final String prefix = isMac ? "darwin" : isLinux ? "linux" : "win32";

        final String resource = prefix + "/" + arch + "/" + libName;
        final URL url = NativeLoader.class.getClassLoader().getResource(resource);
        if (url == null) throw new UnsatisfiedLinkError(resource + " not found on classpath");
        try {
          final Path base = runtimeDir();
          final Path output = Files.createTempFile(base, tempFilePrefix, extension);
          try (final InputStream in = url.openStream();
              final FileChannel channel = FileChannel.open(output, StandardOpenOption.WRITE)) {
            int total = 0;
            int read = 0;
            byte[] buffer = new byte[1024];
            do {
              read = in.read(buffer);
              if (read > 0) channel.write(ByteBuffer.wrap(buffer, 0, read));
            } while (read > 0);
            channel.close();
          } catch (final IOException ex) {
            throw new UnsatisfiedLinkError();
          }
          output.toFile().deleteOnExit();
          if (!pid.isEmpty()) {
            final Path pidFile = Paths.get(output.toString() + ".pid");
            Files.write(pidFile, pid.getBytes());
            pidFile.toFile().deleteOnExit();
          }
          try {
            System.load(output.toString());
          } catch (final UnsatisfiedLinkError e) {
            Files.deleteIfExists(output);
            throw e;
          }
          loaded.set(true);
          final Thread thread = new Thread(new CleanupRunnable(), "ipcsocket-jni-cleanup");
          thread.setDaemon(true);
          thread.start();
          return;
        } catch (final IOException e) {
          throw new UnsatisfiedLinkError(e.getMessage());
        }
      }
      throw new UnsatisfiedLinkError();
    }
  }

  /**
   * This cleans up the temporary shared libraries that are created by NativeLoader. The
   * deleteOnExit calls don't work on windows because the classloader has open handles to the shared
   * libraries. If the process abruptly exits on posix systems, the deleteOnExit calls also aren't
   * run so it is necessary to manually clean up these files to avoid leaking disk space. This is
   * done on a background thread to avoid blocking the application. On windows, we can just try and
   * delete the file and if there is an open handle to the file, the delete will fail, which is what
   * we want. On posix systems, we add a pid file and check if there is a process with that pid
   * running so that we don't accidentally delete an active library from a running process. In some
   * cases, there might be a pid collision that prevents a deletion that could actually be safely
   * performed but this is fairly unlikely and, even if it does happen, is unlikely to lead to an
   * accumulation of temp files because there is unlikely to be another pid collision the next time
   * the collector runs (except in pathological cases).
   */
  private static class CleanupRunnable implements Runnable {
    @Override
    public void run() {
      try {
        Files.walkFileTree(
            runtimeDir(),
            new FileVisitor<Path>() {
              @Override
              public FileVisitResult preVisitDirectory(
                  final Path dir, final BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                  throws IOException {
                if (!file.getFileName().toString().startsWith(tempFilePrefix)) {
                  // do nothing
                  return FileVisitResult.CONTINUE;
                } else if (isWindows) {
                  try {
                    Files.deleteIfExists(file);
                  } catch (final IOException e) {
                  }
                } else if (file.toString().endsWith(".pid")) {
                  final String pid = new String(Files.readAllBytes(file));
                  boolean pidExists = true;
                  final Process process = new ProcessBuilder("ps", "-p", pid).start();
                  try {
                    process.waitFor();
                    final InputStreamReader is = new InputStreamReader(process.getInputStream());
                    final BufferedReader reader = new BufferedReader(is);
                    String line = reader.readLine();
                    while (line != null) {
                      pidExists = line.contains(pid + " ");
                      line = reader.readLine();
                    }
                  } catch (final InterruptedException e) {
                  }
                  if (!pidExists) {
                    Files.deleteIfExists(Paths.get(file.toString().replaceAll(".pid", "")));
                    Files.deleteIfExists(file);
                  }
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                  throws IOException {
                try {
                  Files.deleteIfExists(dir);
                } catch (final DirectoryNotEmptyException e) {
                } catch (final IOException e) {
                  throw e;
                }
                return FileVisitResult.CONTINUE;
              }
            });
      } catch (final IOException e) {
      }
    }
  }
}
