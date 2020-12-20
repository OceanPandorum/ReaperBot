package reaper.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reaper.service.MessageService;

import java.util.Locale;

@Service
public class MessageServiceImpl implements MessageService{

    private final ApplicationContext applicationContext;

    public MessageServiceImpl(@Autowired ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }

    @Override
    public String get(String key){
        return applicationContext.getMessage(key, null, Locale.ROOT);
    }

    @Override
    public String format(String key, Object... args){
        return applicationContext.getMessage(key, args, Locale.ROOT);
    }
}
