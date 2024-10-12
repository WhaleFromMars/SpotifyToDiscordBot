import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer
import javax.sound.sampled.*
import kotlin.math.sqrt

object AudioStreamHandler : AudioSendHandler {

    private val cableName = PeopleBot.dotEnv["CABLE_NAME"] ?: ""

    private var line: TargetDataLine? = null
    private const val BUFFER_SIZE = 960 * 2 // Frames per buffer (20ms of audio at 48kHz) * 2 channels
    private val buffer = ByteArray(BUFFER_SIZE * 2) // *2 because we're using 16-bit samples
    private var lastFrame: ByteBuffer? = null

    init {
        require(cableName.isNotEmpty()) { "CABLE_NAME environment variable not set." }
    }

    fun startCapture(): Boolean {
        return try {
            val format = AudioFormat(48000f, 16, 2, true, true)
            val info = DataLine.Info(TargetDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                println("Line not supported")
                false
            } else {
                val mixer = AudioSystem.getMixerInfo().find { it.name == cableName }?.let { AudioSystem.getMixer(it) }

                if (mixer == null) {
                    println("$cableName not found.")
                    false
                } else {
                    line = (mixer.getLine(info) as TargetDataLine).apply {
                        open(format, bufferSize)
                        start()
                    }
                    println("Audio capture started.")
                    true
                }
            }
        } catch (e: Exception) {
            println("Failed to start capture: ${e.message}")
            false
        }
    }

    override fun canProvide(): Boolean {
        val line = this.line ?: return false

        return try {
            val bytesRead = line.read(buffer, 0, buffer.size).takeIf { it > 0 } ?: run {
                println("Failed to read from line.")
                return false
            }

            if (isSilent(buffer, bytesRead)) {
                lastFrame = null
                return false
            }
            val adjustedBytesRead = applyEffects(bytesRead)
            val potentialFrame = ByteBuffer.wrap(buffer, 0, adjustedBytesRead)
            lastFrame = potentialFrame
            true
        } catch (e: Exception) {
            println("Failed to read line: ${e.message}")
            false
        }
    }

    // Chat-GPT checks if audio is silent, hopefully, my attempted caused white noise x)
    private fun isSilent(buffer: ByteArray, bytesRead: Int, threshold: Double = 0.01): Boolean {
        var sum = 0.0
        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            val normalizedSample = sample / 32768.0
            sum += normalizedSample * normalizedSample
        }
        val rms = sqrt(sum / (bytesRead / 2))
        return rms < threshold
    }

    override fun provide20MsAudio(): ByteBuffer? {
        return lastFrame.also {
            lastFrame = null
        }
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

    private fun applyEffects(bytesRead: Int): Int {
        // Apply effects here
        // Example: bass boost it, wont support speed as its a stream of audio

        return bytesRead
    }
}