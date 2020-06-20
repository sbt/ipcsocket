package org.scalasbt.ipcsocket;

public class Win32SecurityLevel {
  /*
   * Order security levels in increasing strictness.
   */
  public static int NO_SECURITY = 0;
  public static int OWNER_DACL = 1;
  public static int LOGON_DACL = 2;
}
