import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer
import javax.sound.sampled.*

object SoundAudioHandler : AudioSendHandler {
    private val cableName = PeopleBot.dotEnv["CABLE_NAME"]

    private var line: TargetDataLine? = null
    private const val BUFFER_SIZE = 960 * 2 // Frames per buffer (20ms of audio at 48kHz) * 2 channels
    private val buffer = ByteArray(BUFFER_SIZE * 2) // *2 because we're using 16-bit samples
    private var lastFrame: ByteBuffer? = null

    init {
        require(cableName != null) { "CABLE_NAME environment variable not set." }
    }

    fun startCapture(): Boolean {
        try {
            val format = AudioFormat(48000f, 16, 2, true, true)
            val info = DataLine.Info(TargetDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                println("Line not supported")
                return false
            }

            val mixer = AudioSystem.getMixerInfo().find { it.name == cableName }?.let { AudioSystem.getMixer(it) }

            if (mixer == null) {
                println("$cableName not found.")
                return false
            }

            line = (mixer.getLine(info) as TargetDataLine).apply {
                open(format, bufferSize)
                start()
            }

            println("Audio capture started.")
            return true
        } catch (e: Exception) {
            println("Failed to start capture: ${e.message}")
            return false
        }
    }

    override fun canProvide(): Boolean {
        return line?.let { line ->
            try {
                var bytesRead = line.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    bytesRead = applyEffects(bytesRead)
                    lastFrame = ByteBuffer.wrap(buffer, 0, bytesRead)
                    true
                } else {
                    println("Failed to read from line.")
                    false
                }
            } catch (e: Exception) {
                println("Failed to read line: ${e.message}")
                false
            }
        } ?: false
    }

    override fun provide20MsAudio(): ByteBuffer? {
        return lastFrame.also { lastFrame = null }
    }

    override fun isOpus(): Boolean = false

    fun stopCapture() {
        line?.apply {
            stop()
            close()
            line = null
            println("Audio capture stopped.")
        }
    }

    fun applyEffects(bytesRead: Int): Int {
        // Apply effects here
        // Example: bass boost it, wont support speed as its a stream of audio

        return bytesRead
    }
}