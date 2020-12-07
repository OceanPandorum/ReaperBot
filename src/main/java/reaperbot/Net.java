package reaperbot;

import arc.func.Cons;
import arc.util.Strings;
import mindustry.net.Host;
import mindustry.net.NetworkIO;

import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class Net{
    public static final int timeout = 2000;

    public InputStream download(String url){
        try{
            HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
            return connection.getInputStream();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void pingServer(String ip, Cons<Host> listener){
        run(0, () -> {
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

                long start = System.currentTimeMillis();
                socket.receive(packet);

                ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
                listener.get(readServerData(buffer, ip, System.currentTimeMillis() - start));
                socket.disconnect();
            }catch(Exception e){
                listener.get(new Host(0, null, ip, null, 0, 0,
                                      0, null, null, 0, null, null));
            }
        });
    }

    public void run(long delay, Runnable r){
        new Timer().schedule(new TimerTask(){
            @Override
            public void run(){
                if(r != null){
                    r.run();
                }
            }
        }, delay);
    }

    public Host readServerData(ByteBuffer buffer, String ip, long ping){
        return NetworkIO.readServerData((int)ping, ip, buffer);
    }
}
