import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

object PeopleCommands {

    fun joinCommand(event: MessageReceivedEvent) {
        val guild = event.guild
        val channel = event.channel
        val member = event.member ?: return println("Member is null")
        val voiceChannel = Utils.getUserVoiceChannel(member) ?: return println("User is not in a voice channel")

        guild.audioManager.openAudioConnection(voiceChannel)
        channel.sendMessage("Joined voice channel: ${voiceChannel.name}").queue()
    }

    fun playCommand(event: MessageReceivedEvent) {

    }

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
        val songQuery: String = event.getOption("song")?.asString ?: ""
        if (songQuery.isBlank()) {
            event.reply("Please provide a song name or Spotify URL.").setEphemeral(true).queue()
            return
        }

        // Search for the song using the Spotify API
        val tracks = SpotifyHelper.searchTrack(songQuery, returnAmount = 1)
        if (tracks.isNullOrEmpty()) {
            event.reply("No tracks found for your query.").setEphemeral(true).queue()
            return
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
        // Search for tracks based on user input
        val tracks = SpotifyHelper.searchTrack(userInput, returnAmount = 5)

        if (!tracks.isNullOrEmpty()) {
            val options: List<Choice> = tracks.map { track ->
                Choice("${track.name} by ${track.artist.name}", track.id)
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
