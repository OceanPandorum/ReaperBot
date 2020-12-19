package reaperbot;

import arc.func.Cons;
import arc.util.*;
import mindustry.net.*;
import org.springframework.scheduling.annotation.Async;

import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;

public class Net{

    public InputStream download(String url){
        try{
            HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
            return connection.getInputStream();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    @Async
    public void pingServer(String ip, Cons<Host> listener){
        try{
            String resultIP = ip;
            int port = 6567;
            if(ip.contains(":") && Strings.canParsePositiveInt(ip.split(":")[1])){
                resultIP = ip.split(":")[0];
                port = Strings.parseInt(ip.split(":")[1]);
            }

            DatagramSocket socket = new DatagramSocket();
            socket.send(new DatagramPacket(new byte[]{-2, 1}, 2, InetAddress.getByName(resultIP), port));

            socket.setSoTimeout(2000);

            DatagramPacket packet = new DatagramPacket(new byte[256], 256);

            long start = Time.millis();
            socket.receive(packet);

            ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
            listener.get(NetworkIO.readServerData((int)Time.timeSinceMillis(start), ip, buffer));
            socket.disconnect();
        }catch(Exception e){
            listener.get(new Host(0, null, ip, null, 0, 0,
                                  0, null, null, 0, null, null));
        }
    }
}
