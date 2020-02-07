package src.UI

import com.nativejavafx.taskbar.TaskbarProgressbar
import javafx.collections.ObservableList
import javafx.scene.control.ProgressBar
import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import tornadofx.runLater
import tornadofx.*
import java.io.File

class ProgressBarHelper() {
    private var progressBars: List<Pair<String, ProgressBar>> = emptyList<Pair<String, ProgressBar>>()
    private var averageProgress = 0.0
    private lateinit var progressBarsTaskbar: TaskbarProgressbar


    /**
     * Inititalizes progress bars at 0.1 to fix a bug in the HBox
     */
    fun initProgressBars() {
        runLater {
            for (p in progressBars) {
                p.second.progress = 0.01
            }
            updateTaskbarProgressbar()
        }
    }

    /**
     * Updates the progressbar corresponding with a filepath.
     * @param filepath = filepath
     * @param newProgress = new progress for the progressbar
     */
    fun updateProgressBars(filepath: String, newProgress: Double) {
        progressBars.find { it.first == filepath }?.second?.setProgress(newProgress)
        updateTaskbarProgressbar()
        if (newProgress == 1.0)
            updateTaskbarIcon()
    }

    /**
     * Adds a notification to the taskbar icon when 1 or more processes are finished.
     */
    fun updateTaskbarIcon() {
        progressBars.map { it.second }.forEach { println(it.getProgress()) }
        val nrFinishedProcesses = progressBars
            .map { it.second }
            .filter { it.getProgress() == 1.0 }
            .size
        val logoString =
            "res/logos/logo_" + if (nrFinishedProcesses > 9) "9+" else nrFinishedProcesses.toString() + ".png"

        setStageIcon(Image(logoString))
    }

    /**
     * Updates the progressbar in the taskbar.
     */
    fun updateTaskbarProgressbar() {
        averageProgress = progressBars.map { it.second }.map { it.getProgress() }.average()
        progressBarsTaskbar.showOtherProgress(
            (averageProgress * 100).toLong(),
            100, TaskbarProgressbar.TaskbarProgressbarType.NORMAL
        )
    }

    /**
     * This function finds the index of a progress bar by filepath
     *
     * @param filepath is the filepath the progress bar belongs to - used for identification
     *
     * @return the index of the first progress bar in the list of progress bars that belongs to filepath
     */
    fun getIndexForPath(filepath: String): Int {
        return progressBars.indexOf(progressBars.find { it.first == filepath })
    }

    /**
     * Getter for all progressbars with their filepaths
     */
    fun getProgressBars(): List<Pair<String, ProgressBar>> {
        return progressBars
    }

    /**
     * Removes all progressbars from the logic that are full.
     */
    fun removeFinishedProgressbars() {
        progressBars = progressBars.filter { it.second.getProgress() < 1.0 }
    }

    /**
     * Setter for all progressbars with their filepaths
     */
    fun setProgressBars(newProgressBars: List<Pair<String, ProgressBar>>) {
        progressBars = newProgressBars
    }

    /**
     * Initializes progressbar in the taskbar.
     *
     * @param currentStage stage to attach the progressbar in the taskbar to
     */
    fun initTaskBar(currentStage: Stage?) {
        if (!::progressBarsTaskbar.isInitialized) {
            progressBarsTaskbar = TaskbarProgressbar(currentStage)
        }
    }

    /**
     * Instructs the progressbar in the taskbar to show an error.
     */
    fun showErrorProgress() {
        progressBarsTaskbar.showErrorProgress()
    }

    /**
     * Adds new progress bars for every file that is to be analyzed
     */
    fun addNewProgressBars(files: List<File>, progressBars: List<ProgressBar>) {
        if(files.size > progressBars.size)
            return
        this.progressBars = this.progressBars.union(
            files.map {
                it.invariantSeparatorsPath
            }.zip(progressBars)
        ).toList()
    }
}