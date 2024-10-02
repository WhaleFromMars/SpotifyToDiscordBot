import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag

object PeopleBot : ListenerAdapter() {
    val dotEnv = Dotenv.load()
    val GUILD_ID = dotEnv["DISCORD_GUILD_ID"]
    private val token = dotEnv["DISCORD_BOT_TOKEN"]

    private var isStreaming = false

    private val jda = JDABuilder.create(
        token,
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.MESSAGE_CONTENT,
        GatewayIntent.GUILD_VOICE_STATES
    ).disableCache(
        CacheFlag.ACTIVITY,
        CacheFlag.EMOJI,
        CacheFlag.STICKER,
        CacheFlag.CLIENT_STATUS,
        CacheFlag.ONLINE_STATUS,
        CacheFlag.SCHEDULED_EVENTS
    ).setMemberCachePolicy(MemberCachePolicy.VOICE).addEventListeners(this).build()

    init {
        require(token != "") { "Missing environment variable: DISCORD_BOT_TOKEN" }
        require(GUILD_ID != "") { "Missing environment variable: DISCORD_GUILD_ID" }
        SpotifyHelper
        registerCommands()
    }

    private fun registerCommands() {
        jda.updateCommands().addCommands(
            Commands.slash("play", "Adds a song to the queue")
                .addOption(OptionType.STRING, "song", "requires the song name or spotify url", true, true)
                .setGuildOnly(true)
        ).apply { queue() }

    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild?.id != GUILD_ID) return
        //if bot isnt in a channel, join author
        CoroutineScope(Dispatchers.IO).launch {
            when (event.name) {
                "play" -> PeopleCommands.playCommandSlash(event)
            }
        }

        super.onSlashCommandInteraction(event)
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.guild.id != GUILD_ID) return
        if (event.author.isBot) return

        val content = event.message.contentRaw.split(" ").firstOrNull()
        when (content) {
            "!join" -> PeopleCommands.joinCommand(event)
            "!leave" -> leaveVoiceChannel(event.guild)
            "!stream" -> startStreaming(event.guild, event)
            "!stop" -> stopStreaming(event.guild)
        }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.guild?.id != GUILD_ID) return

        CoroutineScope(Dispatchers.IO).launch {
            PeopleCommands.handleAutoComplete(event)
        }
    }

    private fun leaveVoiceChannel(guild: Guild) {
        val audioManager = guild.audioManager
        audioManager.closeAudioConnection()
        stopStreaming(guild)
        println("Left voice channel")
    }

    fun startStreaming(guild: Guild, event: MessageReceivedEvent? = null) {
        if (isStreaming) {
            println("Already streaming")
            event?.channel?.sendMessage("Already streaming")?.queue()
            return
        }

        isStreaming = true
        val audioManager = guild.audioManager


        if (!AudioStreamHandler.startCapture()) {
            isStreaming = false
            event?.channel?.sendMessage("Failed to start audio capture.")?.queue()
            println("Failed to start audio capture.")
            return
        }
        audioManager.sendingHandler = AudioStreamHandler

        println("Started streaming audio from CABLE Output (VB-Audio Virtual Cable)")
        event?.channel?.sendMessage("Started streaming audio from CABLE Output (VB-Audio Virtual Cable)")?.queue()
    }

    private fun stopStreaming(guild: Guild) {
        if (!isStreaming) return

        isStreaming = false
        AudioStreamHandler.stopCapture()
        guild.audioManager.sendingHandler = null
        println("Stopped streaming")
    }
}
