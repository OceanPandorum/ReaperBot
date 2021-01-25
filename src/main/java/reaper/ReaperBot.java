package reaper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Objects;

import static reaper.Constants.config;

@EnableScheduling
@SpringBootApplication
public class ReaperBot{

    @Bean
    public WebFilter webFilter(){
        return (exchange, chain) -> Mono.justOrEmpty(exchange.getRequest())
                .filter(req -> {
                    InetSocketAddress remote = req.getRemoteAddress();
                    InetSocketAddress local = req.getLocalAddress();
                    return remote != null && local != null &&
                            (Objects.equals(remote.getHostString(), local.getHostString()) ||
                            Objects.equals(remote.getHostString(), config.developerIp) ||
                            remote.getAddress().isAnyLocalAddress());
                })
                .then(chain.filter(exchange));
    }

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
}
