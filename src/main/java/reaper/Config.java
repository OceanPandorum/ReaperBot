package reaper;

import discord4j.common.util.Snowflake;

import java.util.*;

import static reaper.Constants.*;

public class Config{
    public Snowflake banListId = Snowflake.of(744854768593141810L);
    public Snowflake commandChannelId = Snowflake.of(744874986073751584L);
    public Snowflake mapsChannelId = Snowflake.of(744906782370955274L);
    public Snowflake schematicsChannelId = Snowflake.of(744906867183976569L);

    public Snowflake adminRoleId = Snowflake.of(747906993259282565L);
    public Snowflake memberRoleId = Snowflake.of(747908856604262469L);

    public Map<String, String> serversMapDirs = Map.of(
            "survival", "/home/servers/survival/config/maps",
            "attack", "/home/servers/attack/config/maps",
            "msgo", "/home/servers/mind-go/config/maps",
            "pvp", "/home/servers/pvp/config/maps"
    );

    public String token;

    public String developerIp;

    public Snowflake developerId = Snowflake.of(564093651589005333L);

    public String prefix = "$";

    public Set<Snowflake> listenedMessages = Collections.emptySet();

    public List<InfoEmbed> info = Collections.emptyList();

    public void update(){
        configFile.writeString(gson.toJson(this));
    }

    public static class InfoEmbed{
        public Snowflake channelId;
        public String title;
        public String description;
        public boolean listenable;
    }
}
