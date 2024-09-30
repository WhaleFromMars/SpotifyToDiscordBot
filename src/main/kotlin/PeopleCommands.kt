import net.dv8tion.jda.api.events.message.MessageReceivedEvent

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
}
