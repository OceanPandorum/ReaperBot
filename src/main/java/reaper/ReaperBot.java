package reaper;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Strings;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.annotation.*;

import javax.annotation.PostConstruct;

import static reaper.Constants.*;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class ReaperBot implements CommandLineRunner{

    @Bean
    public MessageSource messageSource(){
        ResourceBundleMessageSource bundle = new ResourceBundleMessageSource();
        bundle.setBasename("bundle");
        bundle.setDefaultEncoding("utf-8");
        return bundle;
    }

    public static void main(String[] args){
        SpringApplication.run(ReaperBot.class, args);
    }

    @PostConstruct
    public void init(){
        configFile = Fi.get("prefs.json");
        if(!configFile.exists()){
            config = new Config();
            configFile.writeString(gson.toJson(config));
        }
        config = gson.fromJson(configFile.reader(), Config.class);

        cacheDir = new Fi("cache/");
        schemeDir = cacheDir.child("schem/");
        mapDir = cacheDir.child("map/");

        // Создаём схем и мод папку, кеш папка тоже создаться
        schemeDir.mkdirs();
        mapDir.mkdirs();

        contentHandler = new ContentHandler();
        net = new Net();
    }

    @Override
    public void run(String... args) throws Exception{
        Seq<String> arr = Seq.with(args);
        if(arr.any() && arr.get(0).equals("-info")){
            listener.sendInfo(Strings.parseInt(arr.get(1)));
        }
    }
}
