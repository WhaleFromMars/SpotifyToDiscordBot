import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
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

    private var isStreaming = false
    private var audioHandler: SoundAudioHandler? = null

    private fun registerCommands() {
        jda.updateCommands().addCommands(
            Commands.slash("play", "Adds a song to the queue")
                .addOption(OptionType.STRING, "song", "requires the song name or spotify url", true).setGuildOnly(true)
        ).apply { queue() }

    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.guild?.id != GUILD_ID) return

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

    private fun leaveVoiceChannel(guild: Guild) {
        val audioManager = guild.audioManager
        audioManager.closeAudioConnection()
        stopStreaming(guild)
        println("Left voice channel")
    }

    private fun startStreaming(guild: Guild, event: MessageReceivedEvent? = null) {
        if (isStreaming) {
            println("Already streaming")
            event?.channel?.sendMessage("Already streaming")?.queue()
            return
        }

        isStreaming = true
        val audioManager = guild.audioManager

        val handler = SoundAudioHandler()
        if (!handler.startCapture()) {
            isStreaming = false
            event?.channel?.sendMessage("Failed to start audio capture.")?.queue()
            println("Failed to start audio capture.")
            return
        }
        audioHandler = handler
        audioManager.sendingHandler = handler

        println("Started streaming audio from CABLE Output (VB-Audio Virtual Cable)")
        event?.channel?.sendMessage("Started streaming audio from CABLE Output (VB-Audio Virtual Cable)")?.queue()
    }

    private fun stopStreaming(guild: Guild) {
        if (!isStreaming) return

        isStreaming = false
        audioHandler?.stopCapture()
        audioHandler = null
        guild.audioManager.sendingHandler = null
        println("Stopped streaming")
    }
}
