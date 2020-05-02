package org.scalasbt.ipcsocket;

public class NativeErrorException extends Exception {
  private final int code;
  private final String message;

  public NativeErrorException(final int code) {
    this(code, "Native code returned error " + code);
  }

  public NativeErrorException(final int code, final String message) {
    this.code = code;
    this.message = message;
  }

  public int returnCode() {
    return code;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
