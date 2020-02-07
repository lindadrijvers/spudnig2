import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.Alert
import javafx.beans.value.ChangeListener
import javafx.scene.control.ProgressBar
import src.UI.*
import kotlinx.coroutines.*

import java.io.*
import tornadofx.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*

class PoseApp : Controller() {

    private val poseView: PoseView by inject()
    private var poseModel = PoseModel()
    private val childProcesses: List<Process> = ArrayList<Process>()

    fun initProject(){
        poseModel.subscribeView(poseView)
        poseModel.addControllerToTest(this)
        poseModel.fixPythonNecessities()
        poseModel.addProgressBarHelper(poseView.getProgressBarHelper())
    }

    fun getFocusListener(): ChangeListener<Boolean> {
        return poseModel.createFocusListener()
    }

    fun analyze(files: List<File>, progressBars: List<ProgressBar>): Boolean {
        return poseModel.analyze(files, progressBars)
    }

    /**
     * Gets a DataFile from the model and generates a velocity plot from that.
     *
     * @param data data file containing information about analysis
     * @param outputPath directory where velocity plot should be saved
     */
    fun vPlt(data: DataFile, outputPath: String) {
        if (data.plotPath.isBlank()){
            data.plotPath = outputPath
            val opOutput = data.tmpDir
            val args = "$outputPath $opOutput"
            //TODO Check if pathToOPOutput actually opens the csv from openPose, or only the folder that has the csv in it
            GlobalScope.launch {
                try{
                    runInConsole( "python src/spudnig/plotter.py $args")
                }catch (e:RuntimeException){
                    poseView.spudnigError("There was an error while generating the plot, sorry!")
                }
            }
        }else{
            print("Plot already exists") //TODO pop-up
        }
    }

    /**
     * Performs the open pose movement tracking of certain keypoints using the spudnig python script.
     *
     * @param threshold threshold in movement used to determine if something has moved ??
     * @param filepath path under which the file to be analyzed is found at
     * @param savefile path where the output file with the positions of every point is to be saved
     * @param filetype string containing the chosen file extension that the output should have
     * @param kpl list of selected keypoints of left hand
     * @param kpr list of selected keypoints of right hand
     * @param kpb list of selected keypoints of body
     * @param minCutoffValue a movement should be longer than this number of frames to be recognized as a movement
     * @param gapCutoffValue multiple movements less than this number of frames apart are merged
     *
     * @return data file corresponding to this analysis
     */
    fun spudnig(
        threshold: Float,
        filepath: String,
        savefile: String,
        filetype: FileType = FileType.CSV,
        kpl: List<Int>,
        kpr: List<Int>,
        kpb: List<Int>,
        minCutoffValue: Number,
        gapCutoffValue: Number
    ): DataFile {
        poseView.tabPaneFix()
        print("calling spudnig \n")
        val tmpDir = poseModel.createTmpDir(prefix="spudnig", suffix="")
        val tmpDirString = tmpDir.invariantSeparatorsPath
        val filetypeString = if (filetype == FileType.EAF || filetype == FileType.JSON) FileType.CSV.type else filetype.type
        val kplString = kpl.joinToString(",", "\"[", "]\"")
        val kprString = kpr.joinToString(",", "\"[", "]\"")
        val kpbString = kpb.joinToString(",", "\"[", "]\"")
        val args = "$threshold $minCutoffValue $gapCutoffValue \"$filepath\" \"$tmpDirString\" \"$savefile\" $filetypeString -kpl $kplString -kpr $kprString -kpb $kpbString"

        val command = "python src/spudnig/spudnig_new.py $args"

        var error: Throwable? = null

        //creating the used data file
        var data = DataFile(
            File(filepath),
            threshold,
            minCutoffValue,
            gapCutoffValue,
            filepath,
            tmpDir,
            savefile,
            filetype,
            kpl, kpr, kpb,
            error
        )

        val handler = CoroutineExceptionHandler{ _, exception ->
            error = exception
        }
        GlobalScope.launch(handler) {
            try {
                coroutineScope(){
                    runInConsole(command, filepath)
                }
                coroutineScope() {
                    if (filetype != FileType.CSV) {
                        val savedFileName = filepath.split("/").last()
                        val fileToConvert =
                            savefile + "/" + savedFileName.substring(0, savedFileName.length - 4) + ".csv"
                        //println(savefile + " " + filepath)
                        println(fileToConvert)
                        if (filetype == FileType.EAF)
                            createEAFFile(File(fileToConvert), filepath)
                        else
                            toJSON(File(fileToConvert))
                    }
                }
                data.done.set(true)
            }catch (e:RuntimeException){
                error = e
            }
        }

        return data
    }

    /**
     * Takes some string and treats it as a console command.
     * The command is run in a separate thread and errors will be displayed.
     *
     * @param command is the command to be executed
     * @param filepath is an optional parameter used when running spudnig, if it is present,
     * the given filepath is used to determine the progress of the script and update
     * the progress bar accordingly.
     */
    private suspend fun runInConsole(command:String, vararg filepath: String){
        try {
            var p: Process = Runtime.getRuntime().exec(command)
            childProcesses.plus(p)
            var r: BufferedReader = BufferedReader(InputStreamReader(p.inputStream))
            r.forEachLine {
                println(it)
                if (it.matches(Regex("""\d+/\d+""")) and filepath.isNotEmpty())
                    runLater { poseView.updateProgressBars(filepath.first(), parseFraction(it)) }

                if(it.contains("No movement detected"))
                    //TODO why is this not raiseAnalyzingError()?
                    runLater { poseView.spudnigError("There were no movements detected for the selected keypoints") }
            }
            var error: BufferedReader = BufferedReader(InputStreamReader(p.errorStream))
            var errorMessage = error.readLines().joinToString("\n")
            if (errorMessage.length > 0) {
                runLater {
                    if(filepath.isNotEmpty()){
                        poseView.raiseAnalyzingError(filepath.first())
                    }
                    //TODO should this not be done in the spudnig function, not the general console runner?
                    alert(Alert.AlertType.ERROR, "Error in spudnig", errorMessage,
                        actionFn = {
                            runLater { poseView.removeAnalyzingError() }
                    }, owner = PoseView().currentWindow)
                    //TODO make this a poseView.spudnigError(errorMessage) with the same button(action)
                }
                throw RuntimeException("error running the script")
            }
                println("script finished \n")
        } catch (e: Exception) {
            throw RuntimeException("error using a python script")
        }
    }

    private fun parseFraction(fraction: String): Double {
        var numbers = fraction.split("/")
        return numbers[0].toDouble() / numbers[1].toDouble()
    }

    /**
     * Reads CSV output file and builds an XML file that works as an EAF file, based on a template
     * Saves it in the same folder as the input file
     *
     * @param outputData csv containing data
     * @param videoFilePath filepath of video used to generate csv
     */
    private fun createEAFFile(outputData : File, videoFilePath : String) {
        println("Starting EAF Creation")
        //Read output from csv file
        var timeslotCounter = 1
        var timeslots = mutableListOf<String>()

        val buffReader : BufferedReader = BufferedReader(FileReader(outputData))
        // to the location of the used file
        var row = buffReader.readLine()
        while (row != null) {
            //Create the timeslots ts_
            val rowData = row.split(",")
            for (i in arrayOf(2,3)) {
                val time = toMS(rowData[i])
                val timeslot = "<TIME_SLOT TIME_SLOT_ID=\"ts$timeslotCounter\" TIME_VALUE=\"$time\"/>\n"
                timeslotCounter++
                timeslots.add(timeslot)
            }
            row = buffReader.readLine()
        }
        buffReader.close()

        //Create new empty file
        val videoName = videoFilePath.split("/").last()
        val videoNameNoExt = videoName.substring(0,videoName.length-4)
        val fileName =
            videoNameNoExt + "_" + LocalDateTime.now().year + LocalDateTime.now().monthValue + LocalDateTime.now().dayOfMonth + "T" +
                    LocalDateTime.now().hour + LocalDateTime.now().minute   //TODO change name to something appropriate
        var destPath = Paths.get(outputData.canonicalPath).parent.toString()
        destPath+= "\\$fileName.eaf"
        val dest: File = File(destPath)

        //read template file from templates/BlankTemplate.eaf, delimiting on whitespaces
        val source: File = File("templates/BlankTemplate.eaf")
        val scanner: Scanner = Scanner(source)
        var listOfSourceWords: MutableList<String> = mutableListOf<String>()
        while (scanner.hasNextLine()) {
            val sourceRow = scanner.nextLine()
            listOfSourceWords.addAll(sourceRow.split(' '))
        }
        scanner.close()

        //Fill in the blanks found in /templates/BlankTemplate.eaf
        var y = 0
        while (y < listOfSourceWords.size) {
            val word = listOfSourceWords[y]
            if (word == "AUTHOR=")
                listOfSourceWords[y] = word + "\"Me\""  //TODO Change name to something appropriate
            if (word == "DATE=")
                listOfSourceWords[y] = word + "\"" + LocalDateTime.now() + "\""
            if (word == "MEDIA_FILE=")
                listOfSourceWords[y] = word + "\"\""    //TODO What's this?
            if (word == "MEDIA_URL=")
                listOfSourceWords[y] = word + "\"file:///$videoFilePath\""
            if (word == "<TIME_ORDER>") {
                for (time in timeslots.reversed()) {
                    listOfSourceWords.add(y + 1, time)
                }
            }
            if (word == "TIER_ID=\"Movements\">") {
                for (t in timeslots.size - 1 downTo 0 step 2) {
                    listOfSourceWords.add(y + 1, "</ANNOTATION>")
                    listOfSourceWords.add(y + 1, "</ALIGNABLE_ANNOTATION>")
                    listOfSourceWords.add(y + 1, "<ANNOTATION_VALUE>movement</ANNOTATION_VALUE>")
                    listOfSourceWords.add(
                        y + 1, "<ALIGNABLE_ANNOTATION ANNOTATION_ID=" +
                                "\"a" + (t + 1) / 2 + "\"" +
                                " TIME_SLOT_REF1=" +
                                "\"ts" + t + "\"" +
                                " TIME_SLOT_REF2=" +
                                "\"ts" + (t + 1) + "\">"
                    )
                    listOfSourceWords.add(y + 1, "<ANNOTATION>")
                }

            }
            y++
        }

        //Write to the new file
        Files.write(dest.toPath(), listOfSourceWords, StandardCharsets.UTF_8)
        println("Writing done, See $fileName")

        //Delete the old csv with data in it
        Files.delete(outputData.toPath())
    }

    /**
     * Transforms a string time format (hh:mm:ss.dscsms) to milliseconds (ms)
     *
     * @param string containing time information
     *
     * @return amount of ms
     */
    private fun toMS(time: String): Int {
        val timeSplit = time.split(":")
        val hoursInms = timeSplit[0].toDouble() * 3600000
        val minutesInms = timeSplit[1].toDouble() * 60000
        val secondsInms = timeSplit[2].toDouble() * 1000
        return (hoursInms + minutesInms + secondsInms).toInt()
    }

    /**
     * Converts a csv file to JSON format and saves it in same directory as input csv
     *
     * @param csv file to be converted
     */
    public fun toJSON(csv : File) {
        //Read lines from csv file
        var lines = mutableListOf<List<String>>()
        val reader = FileReader(csv)
        for (line in reader.readLines()) {
            lines.add(line.split(","))
        }
        reader.close()

        //Create new json file and writer
        var json = File(csv.canonicalPath.substring(0,csv.canonicalPath.length-4) + ".json")
        val writer = BufferedWriter(FileWriter(json))

        //Loop through extracted csv information and write to json
        writer.write("{\n")
        for (list in lines) {
            writer.write(
                "\t\"" + list[0] + "\" : " + "{\n" +
                    "\t\t\"TIER\" : \"" + list[1] + "\",\n" +
                    "\t\t\"START_TIME\" : \"" + list[2] + "\",\n" +
                    "\t\t\"END_TIME\" : \"" + list[3] + "\",\n" +
                    "\t\t\"TYPE\" : \"" + list[4] + "\"\n" +    //Don't forget to add a comma here if you extend
                    //Add more stuff here if movements2.py is ever extended, also, check above ^
                    "\t}"
            )
            if (lines.indexOf(list) != lines.size-1)
                writer.write(",\n")
            else
                writer.write("\n")
        }
        writer.write("}")
        writer.close()

        //Delete old csv file
        Files.delete(csv.toPath())
    }

    /**
     * Specifies functions to be executed on shutdown
     */
    fun execOnClose() {
        poseModel.execOnClose()
    }

    fun getKPLAsInt(): List<Int> {
        return poseModel.getKPLAsInt()
    }

    fun getKPRAsInt(): List<Int> {
        return poseModel.getKPRAsInt()
    }

    fun getKPBAsInt(): List<Int> {
        return poseModel.getKPBAsInt()
    }

    fun getKPL(): MutableList<SimpleBooleanProperty> {
        return poseModel.getKeypointsLeft()
    }

    fun getKPR(): MutableList<SimpleBooleanProperty> {
        return poseModel.getKeypointsRight()
    }

    fun getKPB():MutableList<SimpleBooleanProperty> {
        return poseModel.getKeypointsBody()
    }

    fun getDataFiles():HashMap<String, DataFile>{
        return poseModel.getDataFiles()
    }

}