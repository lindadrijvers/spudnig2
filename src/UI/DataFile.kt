package src.UI

import tornadofx.toProperty
import java.io.File

/**
 * This class stores all data associated with the analysis of a video.
 */
class DataFile(
    val file: File = File(""),
    val threshold: Number = 0.3,
    val cutoffFramesThreshold: Number = 3,
    val cutoffGapThreshold: Number = 3,
    val filepath: String,
    val tmpDir: File,
    val savefile: String,
    val filetype: FileType = FileType.CSV,
    val kpl: List<Int>,
    val kpr: List<Int>,
    val kpb: List<Int>,
    private val error: Throwable?,
    private val doneAlready: Boolean = false
) {

    var done = doneAlready.toProperty()

    var plotPath: String = ""

    /**
     * Returns whether an error occurred in relation to the processes around this datafile.
     *
     * @return error status
     */
    fun hasError(): Boolean {
        return error != null
    }

    /**
     * Getter for the temporary directory where the temporary files for this datafile are stored.
     *
     * @return temporary directory
     */
    fun getTempDir(): File {
        return tmpDir
    }
}