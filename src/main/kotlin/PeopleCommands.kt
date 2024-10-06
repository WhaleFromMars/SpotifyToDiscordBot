import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

object PeopleCommands {

    fun nowPlayingChannelSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)
        val guild =
            event.guild ?: return event.reply("This command is only available in servers").setEphemeral(true).queue()

        if (guild.id != PeopleBot.GUILD_ID) return event
            .reply("This bot is configured for a different server")
            .setEphemeral(true).queue()

        val channel =
            event.getOption("channel")?.asChannel ?: return event.reply("Please provide a channel").setEphemeral(true)
                .queue()
        val channelID = channel.id

        PeopleBot.EMBED_CHANNEL_ID = channelID
        PeopleBot.CURRENT_TRACK_EMBED_ID = ""
        channel.asTextChannel().sendMessageEmbeds(SpotifyHelper.createEmbed()).queue { message ->
            PeopleBot.CURRENT_TRACK_EMBED_ID = message.id
            PeopleBot.saveMessageIDs()
            SpotifyHelper.addReactionsToMessage(message)
        }

        event.reply("Now playing channel set to ${channel.asMention}").setEphemeral(true).queue()
    }

    fun clearQueueSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)
        if (SpotifyPlayer.queue.isEmpty()) return event
            .reply("The queue is empty you muppet")
            .setEphemeral(true).queue()

        SpotifyPlayer.clearQueue()
        event.reply("Queue cleared").setEphemeral(true).queue()
    }

    fun removeSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)
        val userInput = event.getOption("index").toString()

        if (SpotifyPlayer.queue.isEmpty()) return event
            .reply("The queue is empty you muppet")
            .setEphemeral(true).queue()

        if (userInput.startsWith("index:")) {
            userInput.removePrefix("index:")
            val removedSong = SpotifyPlayer.queue.removeAt(userInput.toInt())
            return event.reply("Removed ${removedSong.name}").setEphemeral(true).queue()
        } else {
            try {
                userInput.toInt()
                val removedSong = SpotifyPlayer.queue.removeAt(userInput.toInt() - 1)
                return event.reply("Removed ${removedSong.name}").setEphemeral(true).queue()
            } catch (_: Exception) {
                return event.reply("Invalid index or song").setEphemeral(true).queue()
            }
        }
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

        val tracks = when {
            songQuery.startsWith("https://open.spotify.com/track/") -> {
                val trackId = songQuery.substringAfter("https://open.spotify.com/track/").substringBefore("?si")
                val track = SpotifyHelper.getTrack(trackId) ?: run {
                    event.reply("Couldnt match provided link: $songQuery").setEphemeral(true).queue()
                    return
                }
                listOf(track)
            }

            songQuery.startsWith("id:") -> {
                val trackId = songQuery.substringAfter("id:")
                val track = SpotifyHelper.getTrack(trackId) ?: run {
                    event.reply("Something went wrong").setEphemeral(true).queue()
                    return
                }
                listOf(track)
            }

            else -> {
                val searchedTracks = SpotifyHelper.searchTrack(songQuery, returnAmount = 1)
                if (searchedTracks.isNullOrEmpty()) {
                    event.reply("No tracks found for your query.").setEphemeral(true).queue()
                    return
                }
                searchedTracks
            }
        }

        val track = tracks.first()
        SpotifyPlayer.addToQueue(track)
        PeopleBot.startStreaming(guild)
        event.reply("Added ${track.name} to the queue.").setEphemeral(true).queue()
    }

    suspend fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        when {
            event.name == "play" && event.focusedOption.name == "song" -> {
                val userInput = event.focusedOption.value
                if (userInput.isEmpty()) return event.replyChoices(emptyList()).queue()

                val tracks = SpotifyHelper.searchTrack(userInput, returnAmount = 5)

                if (tracks.isNullOrEmpty()) return event.replyChoices(emptyList()).queue()


                val options = tracks.map { track ->
                    val trackName = if (track.name.length < 50) track.name else "${track.name.take(50)}..."
                    val artistName = if (track.artist.length < 40) track.artist else "${track.artist.take(40)}..."
                    Choice("$trackName by $artistName", "id:${track.id}")
                }

                event.replyChoices(options).queue()
            }

            event.name == "remove" && event.focusedOption.name == "index" -> {
                val userInput = event.focusedOption.value
                if (userInput.isEmpty()) event.replyChoices(emptyList()).queue()

                val tracks = SpotifyPlayer.queue

                if (tracks.isEmpty()) event.replyChoices(emptyList()).queue()

                val options = tracks.mapIndexedNotNull { i, track ->
                    if (i.toString().startsWith(userInput) || track.name.contains(
                            userInput, ignoreCase = true
                        ) || track.artist.contains(userInput, ignoreCase = true)
                    ) {
                        val trackName = if (track.name.length < 45) track.name else "${track.name.take(45)}..."
                        val artistName = if (track.artist.length < 30) track.artist else "${track.artist.take(30)}..."
                        Choice("${i + 1}. $trackName by $artistName", "index:$i")
                    } else null
                }.take(25)

                event.replyChoices(options).queue()
            }

            else -> {
                event.replyChoices(emptyList()).queue()
            }
        }
    }
}
