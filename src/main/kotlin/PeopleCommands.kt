import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice

object PeopleCommands {

    fun nowPlayingChannelSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)

        val channel =
            event.getOption("channel")?.asChannel ?: return event.reply("Please provide a channel").setEphemeral(true)
                .queue()

        Cache.channelID = channel.id
        Cache.messageID = ""
        channel.asTextChannel().sendMessageEmbeds(SpotifyHelper.createEmbed()).queue { message ->
            Cache.messageID = message.id
            Cache.saveChannelAndMessageIDs()
            SpotifyHelper.addReactionsToMessage(message)
        }

        event.reply("Now playing channel set to ${channel.asMention}").setEphemeral(true).queue()
    }

    fun clearQueueSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)
        if (SpotifyPlayer.queue.isEmpty()) return event.reply("The queue is empty you muppet").setEphemeral(true)
            .queue()

        SpotifyPlayer.clearQueue()
        event.reply("Queue cleared").setEphemeral(true).queue()
    }

    fun removeSlashCommand(event: SlashCommandInteractionEvent) {
        event.deferReply(true)

        if (SpotifyPlayer.queue.isEmpty()) {
            event.reply("The queue is empty, you muppet.").setEphemeral(true).queue()
            return
        }

        val option = event.getOption("index")
        val userInput = option?.asString

        if (userInput.isNullOrBlank()) {
            event.reply("Please provide a valid index or song name.").setEphemeral(true).queue()
            return
        }

        val queueSize = SpotifyPlayer.queue.size

        val index = userInput.toIntOrNull()?.let { it - 1 }
        val removedSong = if (index != null && index in 0 until queueSize) {
            val track = SpotifyPlayer.queue[index]
            SpotifyPlayer.removeFromQueue(track)
            track
        } else {
            val trackIndex = SpotifyPlayer.queue.indexOfFirst { track ->
                track.name.equals(userInput, ignoreCase = true) || track.artist.equals(userInput, ignoreCase = true)
            }
            if (trackIndex != -1) {
                val track = SpotifyPlayer.queue[trackIndex]
                SpotifyPlayer.removeFromQueue(track)
                track
            } else {
                return event.reply("No matching song found in the queue.").setEphemeral(true).queue()
            }
        }

        event.reply("Removed **${removedSong.name}** by **${removedSong.artist}** from the queue.").queue()
    }

    fun whoSlashCommand(event: SlashCommandInteractionEvent) {
        val option = event.getOption("index")
        val userInput = option?.asString
        if (userInput == null) {
            val requesterID =
                SpotifyPlayer.currentTrack?.requesterID ?: return event.reply("Unknown Requester").setEphemeral(true)
                    .queue()

            return event.reply("Song Requested By: <@${requesterID}>").setEphemeral(true).queue()
        }

        val queueSize = SpotifyPlayer.queue.size

        val index = userInput.toIntOrNull()?.let { it - 1 }
        val querySong = if (index != null && index in 0 until queueSize) {
            SpotifyPlayer.queue[index]
        } else {
            val trackIndex = SpotifyPlayer.queue.indexOfFirst { track ->
                track.name.equals(userInput, ignoreCase = true) || track.artist.equals(userInput, ignoreCase = true)
            }
            if (trackIndex != -1) {
                SpotifyPlayer.queue[trackIndex]
            } else {
                return event.reply("No matching song found in the queue.").setEphemeral(true).queue()
            }
        }

        event.reply("Song Requested By: <@${querySong.requesterID}>").setEphemeral(true).queue()
    }


    suspend fun playSlashCommand(event: SlashCommandInteractionEvent) {
        val member = event.member ?: return
        val guild = event.guild ?: return

        val authorVoiceChannel = member.voiceState?.channel
        if (authorVoiceChannel == null) {
            return event.reply("You need to be in a voice channel to use this command.").setEphemeral(true).queue()
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
            return event.reply("Please provide a song name or Spotify URL.").setEphemeral(true).queue()
        }

        when { // Playlist link query
            songQuery.startsWith("https://open.spotify.com/playlist/") -> {
                val playlistId = songQuery.substringAfter("https://open.spotify.com/playlist/").substringBefore("?si")
                val playlist = SpotifyHelper.getPlaylist(playlistId)
                if (playlist == null) {
                    return event.reply("Couldn't find the playlist.").setEphemeral(true).queue()
                }
                val playlistName = playlist.name
                val totalTracks = playlist.tracks.total

                event.reply("Processing $totalTracks tracks from **$playlistName**.").setEphemeral(true).queue()

                SpotifyHelper.addPlaylistToQueue(playlist)
                return // Exit as we have already replied
            }

            // Track link query
            songQuery.startsWith("https://open.spotify.com/track/") -> {
                val trackId = songQuery.substringAfter("https://open.spotify.com/track/").substringBefore("?si")
                val track = SpotifyHelper.getTrack(trackId) ?: run {
                    return event.reply("Couldn't match provided link: $songQuery").setEphemeral(true).queue()
                }
                track.requesterID = event.user.id
                SpotifyPlayer.addToQueue(track)
                PeopleBot.startStreaming(guild)
                return event.reply("Added **${track.name}** to the queue.").setEphemeral(true).queue()
            }

            // Autocomplete query
            songQuery.startsWith("id:") -> {
                val trackId = songQuery.substringAfter("id:")
                val track = SpotifyHelper.getTrack(trackId) ?: run {
                    return event.reply("Something went wrong").setEphemeral(true).queue()
                }
                track.requesterID = event.user.id
                SpotifyPlayer.addToQueue(track)
                PeopleBot.startStreaming(guild)
                return event.reply("Added **${track.name}** to the queue.").setEphemeral(true).queue()
            }

            // String input query
            else -> {
                val searchedTracks = SpotifyHelper.searchTrack(songQuery, returnAmount = 1)
                if (searchedTracks.isNullOrEmpty()) {
                    return event.reply("No tracks found for your query.").setEphemeral(true).queue()
                }
                val track = searchedTracks.first()
                track.requesterID = event.user.id
                SpotifyPlayer.addToQueue(track)
                PeopleBot.startStreaming(guild)
                return event.reply("Added **${track.name}** to the queue.").setEphemeral(true).queue()
            }
        }
    }

    suspend fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        when {
            event.name == "play" && event.focusedOption.name == "song" -> {
                val userInput = event.focusedOption.value
                if (userInput.isEmpty() || userInput.startsWith("https://open.spotify.com/")) {
                    return event.replyChoices(emptyList()).queue()
                }

                val tracks = SpotifyHelper.searchTrack(userInput, returnAmount = 5)

                if (tracks.isNullOrEmpty()) {
                    return event.replyChoices(emptyList()).queue()
                }

                val options = tracks.map { track ->
                    val trackName = if (track.name.length < 50) track.name else "${track.name.take(50)}..."
                    val artistName = if (track.artist.length < 40) track.artist else "${track.artist.take(40)}..."
                    Choice("$trackName - $artistName", "id:${track.id}")
                }

                event.replyChoices(options).queue()
            }

            event.name == "remove" && event.focusedOption.name == "index" -> {
                val userInput = event.focusedOption.value

                val tracks = SpotifyPlayer.queue

                if (tracks.isEmpty()) {
                    event.replyChoices(emptyList()).queue()
                    return
                }

                val options = tracks.mapIndexedNotNull { i, track ->
                    val displayIndex = i + 1
                    val indexString = displayIndex.toString()
                    val matchesInput = userInput.isEmpty() || indexString.startsWith(userInput) || track.name.contains(
                        userInput, ignoreCase = true
                    )

                    if (matchesInput) {
                        val trackName = if (track.name.length <= 45) track.name else "${track.name.take(45)}..."
                        val artistName = if (track.artist.length <= 30) track.artist else "${track.artist.take(30)}..."
                        Choice("$displayIndex. $trackName - $artistName", "$displayIndex")
                    } else {
                        null
                    }
                }.take(25)

                event.replyChoices(options).queue()
            }

            event.name == "who" && event.focusedOption.name == "index" -> {
                val userInput = event.focusedOption.value

                val tracks = SpotifyPlayer.queue

                if (tracks.isEmpty()) {
                    event.replyChoices(emptyList()).queue()
                    return
                }

                val options = tracks.mapIndexedNotNull { i, track ->
                    val displayIndex = i + 1
                    val indexString = displayIndex.toString()
                    val matchesInput = userInput.isEmpty() || indexString.startsWith(userInput) || track.name.contains(
                        userInput, ignoreCase = true
                    )

                    if (matchesInput) {
                        val trackName = if (track.name.length <= 45) track.name else "${track.name.take(45)}..."
                        val artistName = if (track.artist.length <= 30) track.artist else "${track.artist.take(30)}..."
                        Choice("$displayIndex. $trackName - $artistName", "$displayIndex")
                    } else {
                        null
                    }
                }.take(25)

                event.replyChoices(options).queue()
            }

            else -> {
                event.replyChoices(emptyList()).queue()
            }
        }
    }
}
