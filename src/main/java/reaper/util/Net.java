package reaper.util;

import java.io.InputStream;
import java.net.*;

public abstract class Net{

    private Net(){}

    public static InputStream download(String url){
        try{
            return new URL(url).openConnection().getInputStream();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
