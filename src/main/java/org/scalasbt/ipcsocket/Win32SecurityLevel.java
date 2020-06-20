package org.scalasbt.ipcsocket;

public class Win32SecurityLevel {
  /*
   * Order security levels in increasing strictness.
   */
  public static int NO_SECURITY = 0;
  public static int OWNER_DACL = 1;
  // LOGON_DACL must match the value in JNIWin32NamedPipeLibraryProvider.c
  public static int LOGON_DACL = 2;
}
