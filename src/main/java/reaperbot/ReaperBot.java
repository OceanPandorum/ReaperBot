package reaperbot;

import java.io.File;

public class ReaperBot {
    public static final String releasesURL = "https://api.github.com/repos/Anuken/Mindustry/releases";
    public static final long guildID = 741011186916524213L;
    public static final File prefsFile = new File("prefs.properties");
    public static final long bugReportChannelID = 391073027309305856L; //
    public static final long modChannelID = 663227511987240980L; //
    public static final long pluginChannelID = 744853784038998058L;
    public static final long crashReportChannelID = 467033526018113546L; //
    public static final long serverChannelID = 517896556029149214L; //
    public static final long logChannelID = 568416809964011531L; //
    public static final long commandChannelID = 744874986073751584L;
    public static final long announcementsChannelID = 746000026269909002L; //
    public static final long screenshotsChannelID = 553071673587400705L; //
    public static final long suggestionsChannelID = 640604827344306207L; //
    public static final long mapsChannelID = 744906782370955274L;
    public static final long moderationChannelID = 488049830275579906L; //
    public static final long schematicsChannelID = 744906867183976569L;
    public static final boolean sendWelcomeMessages = false;

    public static final long messageDeleteTime = 20000;

    public static ServerBridge server = new ServerBridge();
    public static ContentHandler contentHandler = new ContentHandler();
    public static Messages messages = new Messages();
    public static Commands commands = new Commands();
    public static Net net = new Net();
    public static Prefs prefs = new Prefs(prefsFile);

    public static void main(String[] args){
        new ReaperBot();
    }
}
