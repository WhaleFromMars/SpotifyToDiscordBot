import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

object Utils {
    fun getUserVoiceChannel(user: Member?): VoiceChannel? = user?.voiceState?.channel?.asVoiceChannel()
}