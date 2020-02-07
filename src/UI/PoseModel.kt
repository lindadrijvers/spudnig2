package src.UI

import PoseApp
import PoseView
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.control.ProgressBar
import kotlinx.coroutines.selects.select
import tornadofx.information
import tornadofx.isInt
import java.io.File
import kotlin.system.exitProcess

class PoseModel {

    private var subscribedViews = emptyList<PoseView>()

    private val childProcesses: List<Process> = emptyList<Process>()

    // variables about files
    private val selectedFiles = FXCollections.observableArrayList<File>()
    private val dataFiles = HashMap<String, DataFile>()
    private var tmpDirs = emptyList<File>()
    private var outputFolder = File(System.getProperty("user.dir"))
    private var fileFormat = FileType.CSV

    // variables about selected keypoints
    private val keypointsLeft = mutableListOf<SimpleBooleanProperty>()
    private val keypointsRight = mutableListOf<SimpleBooleanProperty>()
    private val keypointsBody = mutableListOf<SimpleBooleanProperty>()

    // variables about other inputs for spudnig
    private val thresholdValue = SimpleDoubleProperty(0.3)
    private val minThresholdValue = SimpleIntegerProperty(4)
    private val gapThresholdValue = SimpleIntegerProperty(4)

    // classes to help the model
    private lateinit var progressBarHelper: ProgressBarHelper
    private val pythonHelper = PythonHelper()
    private lateinit var poseApp: PoseApp

    /**
     * Subscribes a view to updates from this model
     */
    fun subscribeView(view: PoseView) {
        subscribedViews = subscribedViews.plus(view)
    }

    fun addControllerToTest(controller: PoseApp){
        poseApp = controller
    }

    /**
     * Creates a temprary directory, and adds it to the list of all temporary directories
     */
    fun createTmpDir(prefix: String, suffix: String = ""): File {
        val tmpDir = createTempDir(prefix, suffix)
        tmpDirs = tmpDirs.plus(tmpDir)
        return tmpDir
    }

    /**
     * Deletes temporary files
     *
     * @param tmpFile temporary directory
     *
     * @return true if operation was successful, false otherwise
     */
    fun deleteTmpDir(tmpDir: File): Boolean {
        return if (tmpDir.exists()) tmpDir.deleteRecursively() else false
    }

    /**
     * Checks/installs python necessities at start of program
     */
    fun fixPythonNecessities() {
        if (!pythonHelper.checkSupportedPythonInstalled()){
            subscribedViews.forEach {
                println("Does this happen?")
                it.raiseError(
                    header = "Python not installed correctly",
                    content = "It seems that python is not correctly installed on your system." +
                            " This means that your system doesn't have python or it has an unsupported version." +
                            " Response when trying to find the python version:\n\n" +
                            pythonHelper.getPythonInstalledResponse() + "\n\n" +
                            "Our program only works with python 3, " +
                            "which you can get here: https://www.python.org/downloads/",
                    actionFn = { exitProcess(1) })
            }
        }
        pythonHelper.installDependencies()
    }

    /**
     * Specifies functions to be executed on shutdown
     */
    fun execOnClose() {
        killChildProcesses()
        tmpDirs.forEach { deleteTmpDir(it) }
    }

    /**
     * Kills all processes initialized by spudnig
     */
    fun killChildProcesses() {
        for (p in childProcesses) {
            p.destroy()
        }
    }

    /**
     * Creates a listener for the focussed property that will reset the taskbar icon.
     *
     * @return listener for when window is focussed
     */
    fun createFocusListener(): ChangeListener<Boolean> {
        return ChangeListener<Boolean>() { observableValue: ObservableValue<out Boolean>?, _: Boolean, _: Boolean ->
            if (observableValue != null) {
                if (observableValue.getValue()) {
                    val logoString = "res/logos/logo.png"
                    tornadofx.setStageIcon(javafx.scene.image.Image(logoString))
                    progressBarHelper.removeFinishedProgressbars()
                }
            }
        }
    }

    fun analyze(files: List<File>, progressBars: List<ProgressBar>): Boolean {
        /*
        In the case there are no key points and or files selected, a warning will be given and tells you what to do.
        The program will not run without key points and/or files
         */
        if (!ensureRequirementsSatisfied(files)){
            return false
        }
        progressBarHelper.addNewProgressBars(files, progressBars)
        subscribedViews.forEach {
            progressBarHelper.initTaskBar(it.currentStage)
        }

        val kpl = getKPLAsInt()
        val kpr = getKPRAsInt()
        val kpb = getKPBAsInt()
        dataFiles.putAll(files
            .map { it.invariantSeparatorsPath }
            .map { Pair (
                it,
                poseApp.spudnig(
                    thresholdValue.get().toFloat(),
                    it,
                    outputFolder.invariantSeparatorsPath,
                    fileFormat,
                    kpl,
                    kpr,
                    kpb,
                    minThresholdValue.value,
                    gapThresholdValue.value
                )
            )}.toMap())
        return true
    }

    /**
     * Checks whether keypoints and files are selected.
     * If not, the GUI is instructed to display an appropiate info screen
     *
     * @return true if analysis can take place, false otherwise
     */
    private fun ensureRequirementsSatisfied(files: List<File>): Boolean {
        val keyPointsSelected = keypointsBody.any { it.get() } || keypointsLeft.any { it.get() } || keypointsRight.any { it.get() }
        println("keypoints selected: " + keyPointsSelected)
        println("Files empty: " + files.isEmpty())
        if (!keyPointsSelected || files.isEmpty()) {
            val noFilesMessage =
                "There are no files added, please add a file by using the \"Manage input file(s)\" button."
            val noKeyPointsMessage =
                "There are no key points selected, please select one or more key points."
            if (files.isEmpty() && !keyPointsSelected)
                information(
                    "Missing input!",
                    content = noFilesMessage + "\n" + noKeyPointsMessage
                )
            else if (!keyPointsSelected)
                information("Missing key points!", content = noKeyPointsMessage)
            else
                information("Missing files!", content = noFilesMessage)
            return false
        }
        return true
    }


    fun getKeypointsLeft(): MutableList<SimpleBooleanProperty> {
        return keypointsLeft
    }

    fun getKeypointsRight(): MutableList<SimpleBooleanProperty> {
        return keypointsRight
    }

    fun getKeypointsBody(): MutableList<SimpleBooleanProperty> {
        return keypointsBody
    }

    fun getKPLAsInt(): List<Int> {
        return kPAsInt(getKeypointsLeft())
    }

    fun getKPRAsInt(): List<Int> {
        return kPAsInt(getKeypointsRight())
    }

    fun getKPBAsInt(): List<Int> {
        return kPAsInt(getKeypointsBody())
    }

    private fun kPAsInt(kPList: MutableList<SimpleBooleanProperty>): List<Int> {
        return kPList
            .filter { it.get() && it.name.isInt() }
            .map { it.name.toInt() }
            .toList()
    }

    fun addProgressBarHelper(progressBarHelper: ProgressBarHelper) {
        this.progressBarHelper = progressBarHelper
    }

    fun getDataFiles(): HashMap<String, DataFile>{
        return dataFiles
    }

}