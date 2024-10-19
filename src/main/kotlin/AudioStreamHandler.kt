import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer
import javax.sound.sampled.*
import kotlin.math.sqrt

object AudioStreamHandler : AudioSendHandler {
    private val cableName = PeopleBot.dotEnv["CABLE_NAME"] ?: ""
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    private var line: TargetDataLine? = null
    private const val BUFFER_SIZE = 960 * 2
    private val buffer = ByteArray(BUFFER_SIZE * 2)
    private var lastFrame: ByteBuffer? = null

    init {
        if (isWindows && cableName.isEmpty()) {
            throw IllegalStateException("CABLE_NAME environment variable must be set for Windows.")
        }
    }

    fun startCapture(): Boolean {
        return try {
            val format = if (isWindows) {AudioFormat(48000f, 16, 2, true, true)} else {
                AudioFormat(44100f, 16, 2, true, true)
            }
            val info = DataLine.Info(TargetDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                println("Line not supported")
                return false
            }
            val mixer = if (isWindows) {
                AudioSystem.getMixerInfo()
                    .find { it.name == cableName || it.description == cableName }
                    ?.let { AudioSystem.getMixer(it) }
            } else {
                AudioSystem.getMixerInfo()
                    .find { it.name.contains("PulseAudio") || it.name.contains("default") }
                    ?.let { AudioSystem.getMixer(it) }
            }

            if (mixer == null) {
                println("No suitable audio source found.")
                return false
            }

            line = (mixer.getLine(info) as TargetDataLine).apply {
                open(format, bufferSize)
                start()
            }
            println("Audio capture started.")
            true
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
            lastFrame = ByteBuffer.wrap(buffer, 0, bytesRead)
            true
        } catch (e: Exception) {
            println("Failed to read line: ${e.message}")
            false
        }
    }

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

    override fun provide20MsAudio(): ByteBuffer? = lastFrame?.also { lastFrame = null }

    override fun isOpus(): Boolean = false

    fun stopCapture() {
        line?.apply {
            stop()
            close()
            line = null
            println("Audio capture stopped.")
        }
    }
}