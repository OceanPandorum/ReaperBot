package reaperbot;

import arc.util.Log;

import java.io.*;
import java.net.Socket;

public class Service {
    public Socket socket;
    public BufferedReader in;
    public BufferedWriter out;

    public Service(Socket socket) throws IOException {
        this.socket = socket;

        in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
    }

    public void shutdown() {
        try {
            if (!socket.isClosed()) {
                socket.close();
                in.close();
                out.close();

                Log.info("Socket server shutdowned...");
            }
        } catch (IOException e){
            Log.err(e);
        }
    }

    public String get() throws IOException {
        out.write("{\"req\":\"server-info\"}\n");
        out.flush();

        return in.readLine();
    }
}
