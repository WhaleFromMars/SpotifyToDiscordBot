import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

object PeopleCommands {

    suspend fun playCommandSlash(event: SlashCommandInteractionEvent) {
        val member = event.member ?: return
        val guild = event.guild ?: return

        event.deferReply(true)
        // Check if the author is in a voice channel
        val memberVoiceState: GuildVoiceState = member.voiceState ?: return
        val authorVoiceChannel = memberVoiceState.channel
        if (authorVoiceChannel == null) {
            event.reply("You need to be in a voice channel to use this command.").setEphemeral(true).queue()
            return
        }

        val botSelfMember = guild.selfMember
        val botVoiceState: GuildVoiceState = botSelfMember.voiceState ?: return
        val botVoiceChannel = botVoiceState.channel


        if (botVoiceChannel != null && botVoiceChannel != authorVoiceChannel && botVoiceChannel.members.size > 1) {
            event.reply("I'm already in a voice channel with other members.").setEphemeral(true).queue()
        } else {
            guild.audioManager.openAudioConnection(authorVoiceChannel)
            guild.audioManager.sendingHandler = AudioStreamHandler
        }

        // Get the song query from the command option
        val songQuery: String = event.getOption("song")?.asString ?: return
        println("Song query: $songQuery")
        if (songQuery.isBlank()) {
            event.reply("Please provide a song name or Spotify URL.").setEphemeral(true).queue()
            return
        }

        var tracks: List<TrimmedTrack>?
        // Search for the song using the Spotify API
        if (songQuery.startsWith("id:")) {
            val trackId = songQuery.substringAfter("id:")
            val track =
                SpotifyHelper.getTrack(trackId) ?: return event.reply("something went wrong").setEphemeral(true).queue()
            tracks = listOf(track)
        } else {
            tracks = SpotifyHelper.searchTrack(songQuery, returnAmount = 1)
            if (tracks.isNullOrEmpty()) {
                event.reply("No tracks found for your query.").setEphemeral(true).queue()
                return
            }
        }

        val track = tracks.first()
        // Add the track to the queue
        SpotifyHelper.addToQueue(track)
        PeopleBot.startStreaming(guild)
        event.reply("Added ${track.name} to the queue.").setEphemeral(true).queue()
        // Respond with an embed showing the added track
//        val embed = SpotifyHelper.createEmbed(track)
//        event.replyEmbeds(embed).queue()
    }

    suspend fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        if (event.name != "play" || event.focusedOption.name != "song") return

        val userInput = event.focusedOption.value
        if (userInput.isEmpty()) return

        val tracks = SpotifyHelper.searchTrack(userInput, returnAmount = 5)

        if (!tracks.isNullOrEmpty()) {
            val options: List<Choice> = tracks.map { track ->
                Choice("${track.name} by ${track.artist.name}", "id:${track.id}")
            }
            event.replyChoices(options).queue()
        } else {
            event.replyChoices(emptyList()).queue()
        }
    }


    fun setMusicChannel(event: MessageReceivedEvent) {
        val guild = event.guild
    }
}
