package reaper;

import discord4j.common.util.Snowflake;

import java.util.*;

import static reaper.Constants.*;

public class Config{

    public Snowflake
    serverChannelId = Snowflake.of(746000026269909002L),
    serverMessageId = Snowflake.of(747117737268215882L),
    commandChannelId = Snowflake.of(744874986073751584L),
    mapsChannelId = Snowflake.of(744906782370955274L),
    schematicsChannelId = Snowflake.of(744906867183976569L);

    public Snowflake
    adminRoleId = Snowflake.of(747906993259282565L),
    memberRoleId = Snowflake.of(747908856604262469L);

    public Snowflake guildId = Snowflake.of(744814929701240882L);

    public String token;

    public String prefix = "$";

    public Set<Snowflake> listenedMessages = Collections.emptySet();

    public Set<String> servers = Collections.emptySet();

    public List<InfoEmbed> info = Collections.emptyList();

    public void update(){
        configFile.writeString(gson.toJson(this));
    }
}
