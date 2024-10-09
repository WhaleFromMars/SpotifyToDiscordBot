import io.github.cdimascio.dotenv.Dotenv
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import java.io.File

object Cache {

    private val dotEnv = Dotenv.load()
    private const val CACHE_RESET_TIME = 1000 * 60 * 60 * 24 * 7 // 7 days
    var channelID: String? = null
    var messageID: String? = null
    var guildID: String = dotEnv["DISCORD_GUILD_ID"]
    private var guild: Guild = PeopleBot.jda.getGuildById(guildID) ?: throw Exception("Guild not found")
    private var embedChannel: TextChannel? = null
    private var cachelastUpdate: Long = 0

    init {
        loadChannelAndMessageIDs()
        populateCache()
    }

    private fun loadChannelAndMessageIDs() {
        if (!File("$guildID.txt").exists()) return
        try {
            File("$guildID.txt").bufferedReader().readLines().let { lines ->
                channelID = lines[0]
                messageID = lines[1]
            }
        } catch (_: Exception) {
            println("Error loading message IDs")
            File("$guildID.txt").delete()
            channelID = ""
            messageID = ""
        }
    }

    fun saveChannelAndMessageIDs() {
        File("$guildID.txt").delete()
        File("$guildID.txt").bufferedWriter().use { writer ->
            writer.write("$channelID\n")
            writer.write("$messageID\n")
        }
    }

    private fun populateCache() {
        if (channelID == null) return
        embedChannel = PeopleBot.jda.getTextChannelById(channelID.toString())

        cachelastUpdate = System.currentTimeMillis()
    }

    fun getGuild(): Guild {
        if (System.currentTimeMillis() - cachelastUpdate > CACHE_RESET_TIME) {
            guild = PeopleBot.jda.getGuildById(guildID) ?: throw Exception("Guild not found")
            cachelastUpdate = System.currentTimeMillis()
        }
        return guild
    }

    fun getChannel(): TextChannel? {
        print(embedChannel)
        if (channelID == null) return null
        if (embedChannel == null || System.currentTimeMillis() - cachelastUpdate > CACHE_RESET_TIME) {
            embedChannel = PeopleBot.jda.getTextChannelById(channelID.toString())
            cachelastUpdate = System.currentTimeMillis()
        }

        if (embedChannel == null) {
            channelID = ""
            messageID = ""
            saveChannelAndMessageIDs()
        }

        return embedChannel
    }
}