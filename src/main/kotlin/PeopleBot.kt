import helpers.EmbedHelper
import helpers.SpotifyHelper
import io.github.cdimascio.dotenv.Dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag

object PeopleBot : ListenerAdapter() {

    val logger = KotlinLogging.logger("PeopleBot")
    val dotEnv = Dotenv.configure().ignoreIfMissing().load()
    private val token = dotEnv["DISCORD_BOT_TOKEN"]
    private val GUILD_ID = dotEnv["DISCORD_GUILD_ID"]

    private val job = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.Default + job)

    val jda = JDABuilder.createDefault(token).enableIntents(
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGE_REACTIONS,
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
        logger.info { "Starting PeopleBot" }
        require(!token.isNullOrEmpty()) { "Missing environment variable: DISCORD_BOT_TOKEN" }
        require(!GUILD_ID.isNullOrEmpty()) { "Missing environment variable: DISCORD_GUILD_ID" }
        EmbedHelper
    }

    override fun onReady(event: ReadyEvent) {
        scope.launch {
            try {
                logger.info { "Bot is ready. Initializing components..." }

                // Initialize Spotify API
                SpotifyHelper.initialize()

                // Initialize other components
                SpotifyPlayer.initialize()

                // Register commands
                registerCommands()

                logger.info { "Initialization complete." }
            } catch (e: Exception) {
                logger.error(e) { "Initialization failed" }
            }
        }
    }

    private fun registerCommands() {
        jda.updateCommands().addCommands(
            Commands.slash("play", "Adds a song to the queue")
                .addOption(OptionType.STRING, "song", "Requires the song name or Spotify URL", true, true)
                .setGuildOnly(true),
            Commands.slash(
                "setnowplayingchannel", "Sets the channel to place the embed messages for current track and the queue"
            ).addOptions(
                OptionData(
                    OptionType.CHANNEL, "channel", "Requires a text channel", true
                ).setChannelTypes(ChannelType.TEXT)
            ).setGuildOnly(true),
            Commands.slash("clearqueue", "Clears the queue").setGuildOnly(true),
            Commands.slash("remove", "Removes a song from the queue").addOption(
                OptionType.STRING, "index", "Requires the index or name of the song in the queue", true, true
            ).setGuildOnly(true),
            Commands.slash("who", "Queries who requested the current/specified song").addOption(
                OptionType.STRING, "index", "Requires the index or name of the song in the queue", false, true
            ).setGuildOnly(true)
        ).queue()
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val guild = event.guild ?: return
        if (guild.id != GUILD_ID) {
            event.reply("This bot is configured for a different server").setEphemeral(true).queue()
            return
        }

        scope.launch {
            when (event.name) {
                "play" -> PeopleCommands.playSlashCommand(event)
                "setnowplayingchannel" -> PeopleCommands.nowPlayingChannelSlashCommand(event)
                "remove" -> PeopleCommands.removeSlashCommand(event)
                "clearqueue" -> PeopleCommands.clearQueueSlashCommand(event)
                "who" -> PeopleCommands.whoSlashCommand(event)
                else -> event.reply("Unknown command").setEphemeral(true).queue()
            }
        }
    }

    override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        if (event.guild.id != GUILD_ID) return
        if (event.messageId != Cache.messageID) return
        if (event.user?.id == jda.selfUser.id) return
        val user = event.user ?: return

        val emoji = event.emoji

        event.channel.asTextChannel().removeReactionById(event.messageId, emoji, user).queue()

        // If the user isn't in the same voice channel as the bot, ignore their reaction
        if (event.member?.voiceState?.channel != event.guild.selfMember.voiceState?.channel) return
        if (SpotifyPlayer.currentTrack == null) return

        when (emoji) {
            Emoji.fromUnicode("\uD83D\uDD00") -> SpotifyPlayer.shuffle()
            Emoji.fromUnicode("\u23EE\uFE0F") -> SpotifyPlayer.playPrevious()
            Emoji.fromUnicode("\u23EF\uFE0F") -> SpotifyPlayer.togglePause()
            Emoji.fromUnicode("\u23ED\uFE0F") -> SpotifyPlayer.skipCurrent()
            Emoji.fromUnicode("\uD83D\uDD01") -> SpotifyPlayer.toggleRepeat()
            Emoji.fromUnicode("\u23F9\uFE0F") -> SpotifyPlayer.shutdownPlayer()
        }
    }

    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.guild?.id != GUILD_ID) return

        scope.launch {
            try {
                PeopleCommands.handleAutoComplete(event)
            } catch (e: Exception) {
                logger.error(e) { "Error handling autocomplete interaction" }
            }
        }
    }

    fun leaveVoiceChannel() {
        val guild = Cache.getGuild()
        val audioManager = guild.audioManager
        stopStreaming()
        audioManager.closeAudioConnection()
    }

    fun startStreaming() {
        val audioManager = Cache.getGuild().audioManager

        if (!AudioStreamHandler.startCapture()) {
            logger.error { "Failed to start audio capture." }
            return
        }
        audioManager.sendingHandler = AudioStreamHandler

        logger.info { "Started streaming audio from CABLE Output (VB-Audio Virtual Cable)" }
    }

    fun stopStreaming() {
        AudioStreamHandler.stopCapture()
        Cache.getGuild().audioManager.sendingHandler = null
        logger.info { "Stopped streaming" }
    }
}
