import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

object PeopleCommands {

    fun nowPlayingChannelSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)
        val guild =
            event.guild ?: return event.reply("This command is only available in servers").setEphemeral(true).queue()

        if (guild.id != PeopleBot.GUILD_ID) return event.reply("This bot is configured for a different server")
            .setEphemeral(true).queue()

        val channel =
            event.getOption("channel")?.asChannel ?: return event.reply("Please provide a channel").setEphemeral(true)
                .queue()
        val channelID = channel.id

        PeopleBot.EMBED_CHANNEL_ID = channelID
        PeopleBot.CURRENT_TRACK_EMBED_ID = ""
        PeopleBot.saveMessageIDs()

        event.reply("Now playing channel set to ${channel.asMention}").setEphemeral(true).queue()
    }

    suspend fun playSlashCommand(event: SlashCommandInteractionEvent) {
        val member = event.member ?: return
        val guild = event.guild ?: return

        event.deferReply(true)
        val authorVoiceChannel = member.voiceState?.channel
        if (authorVoiceChannel == null) {
            event.reply("You need to be in a voice channel to use this command.").setEphemeral(true).queue()
            return
        }

        val botSelfMember = guild.selfMember
        val botVoiceState: GuildVoiceState = botSelfMember.voiceState ?: return
        val botVoiceChannel = botVoiceState.channel

        if (botVoiceChannel != null && botVoiceChannel != authorVoiceChannel && botVoiceChannel.members.size > 1) {
            return event.reply("I'm already in a voice channel with other members.").setEphemeral(true).queue()
        } else {
            guild.audioManager.openAudioConnection(authorVoiceChannel)
            guild.audioManager.sendingHandler = AudioStreamHandler
        }

        val songQuery: String = event.getOption("song")?.asString ?: return
        if (songQuery.isBlank()) {
            event.reply("Please provide a song name or Spotify URL.").setEphemeral(true).queue()
            return
        }

        var tracks: List<TrimmedTrack>?
        if (songQuery.startsWith("id:")) {
            val trackId = songQuery.substringAfter("id:")
            val track =
                SpotifyHelper.getTrack(trackId) ?: return event.reply("Something went wrong").setEphemeral(true).queue()
            tracks = listOf(track)
        } else {
            tracks = SpotifyHelper.searchTrack(songQuery, returnAmount = 1)
            if (tracks.isNullOrEmpty()) {
                event.reply("No tracks found for your query.").setEphemeral(true).queue()
                return
            }
        }

        val track = tracks.first()
        SpotifyPlayer.addToQueue(track)
        PeopleBot.startStreaming(guild)
        event.reply("Added ${track.name} to the queue.").setEphemeral(true).queue()
    }

    suspend fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        if (event.name != "play" || event.focusedOption.name != "song") return

        val userInput = event.focusedOption.value
        if (userInput.isEmpty()) return

        val tracks = SpotifyHelper.searchTrack(userInput, returnAmount = 5)

        if (!tracks.isNullOrEmpty()) {
            val options: List<Choice> = tracks.map { track ->
                Choice("${track.name} by ${track.artist}", "id:${track.id}")
            }
            event.replyChoices(options).queue()
        } else {
            event.replyChoices(emptyList()).queue()
        }
    }
}
