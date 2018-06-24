/*

 Copyright 2004-2017, Martian Software, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 */
package org.scalasbt.ipcsocket;

import com.sun.jna.platform.win32.WinNT.HANDLE;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

public class CloseCallback {

    private static final Win32NamedPipeLibrary API = Win32NamedPipeLibrary.INSTANCE;
    private final LinkedBlockingQueue<HANDLE> connectedHandles;
    private final LinkedBlockingQueue<HANDLE> openHandles;
    private final Win32NamedPipeServerSocket win32NamedPipeServerSocket;

    public CloseCallback(final Win32NamedPipeServerSocket win32NamedPipeServerSocket,
                            final LinkedBlockingQueue<HANDLE> connectedHandles,
                            final LinkedBlockingQueue<HANDLE> openHandles){
        this.win32NamedPipeServerSocket = win32NamedPipeServerSocket;
        this.connectedHandles = connectedHandles;
        this.openHandles = openHandles;
    }

    public CloseCallback(){
        this.win32NamedPipeServerSocket = null;
        this.connectedHandles = null;
        this.openHandles = null;
    }

    public void onNamedPipeSocketClose(HANDLE handle) throws IOException {
        if(win32NamedPipeServerSocket != null
            && connectedHandles != null
            && openHandles != null){
            if (connectedHandles.remove(handle)) {
                win32NamedPipeServerSocket.closeConnectedPipe(handle, false);
            }
            if (openHandles.remove(handle)) {
                win32NamedPipeServerSocket.closeOpenPipe(handle);
            }
        }
    }

}
