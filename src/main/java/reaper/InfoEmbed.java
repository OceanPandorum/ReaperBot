package reaper;

import discord4j.common.util.Snowflake;

public class InfoEmbed{
    public Snowflake channelId;
    public String title;
    public String description;
    public boolean listenable;

    public InfoEmbed(Snowflake channelId, String title, String description, boolean listenable){
        this.channelId = channelId;
        this.title = title;
        this.description = description;
        this.listenable = listenable;
    }
}
