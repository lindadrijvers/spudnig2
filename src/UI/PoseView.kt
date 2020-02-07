import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.text.FontWeight
import javafx.stage.DirectoryChooser
import javafx.stage.Window
import src.UI.*
import tornadofx.*
import java.io.File
import java.io.FileInputStream
import java.util.stream.IntStream
import kotlin.collections.HashMap

class PoseView : View("SPUDNIG") {

    init {
        runLater {
            setWindowMinSize(1000, 850)
        }
        val iconStream = FileInputStream("res/Keypoint_pics/spudnigIcon.png")
        setStageIcon(Image(iconStream))
    }

    private val poseApp: PoseApp by inject()
    private val toggleFormat = ToggleGroup()
    private var files = FXCollections.observableArrayList<File>()//emptyList()
    private var dataFiles: HashMap<String, DataFile> = HashMap()
    private var outputFolder = File(System.getProperty("user.dir"))
    private var fileFormat = FileType.CSV
    private lateinit var progressBarsView: ListView<HBox>

    var thresholdValue: DoubleProperty = SimpleDoubleProperty(0.3)
    var minThresholdValue: IntegerProperty = SimpleIntegerProperty(4)
    var gapThresholdValue: IntegerProperty = SimpleIntegerProperty(4)

    //Initialising containers for keypoint visualisation
    lateinit var keyImgPaneLeft: StackPane
    lateinit var keyImgPaneRight: StackPane

    var keyImagesLeft = mutableListOf<String>()
    var keyImagesRight = mutableListOf<String>()

    private val pythonHelper = PythonHelper()
    private val progressBarHelper = ProgressBarHelper()

    /**
     * executes a workaround solution for the tab explosion bug introduced by TornadoFX.
     */
    fun tabPaneFix() {
        runLater {
            while (root.tabs.size > 2) {
                root.tabs.removeAt(root.tabs.size - 1)
            }
        }
    }

    /**
     * refreshes keypoint image
     *
     * @param side "Right" or "Left" specifying which hand
     * @param keyImgPane StackPane where the images should be
     * @param keyImg List of keypoint names
     */
    private fun refreshImg(side: String, keyImgPane: StackPane, keyImg: MutableList<String>) {
        keyImgPane.children.clear()
        keyImgPane.add(imageview("res/KeyPoint_pics/keypoints_hand${side}.png"))
        for (i in keyImg) {
            keyImgPane.add(imageview(i) { if (side.equals("Left")) scaleX = -scaleX })
        }
    }

    /**
     * Fills a list with SimpleBooleanProperties corresponding to keypoints
     *
     * @param list list to be filled
     */
    fun addKeypoints(list: MutableList<SimpleBooleanProperty>) {
        val parts = listOf("Palm", "Thumb", "Index finger", "Middle finger", "Ring finger", "Pinky")
        for (part in parts)
            list.add(SimpleBooleanProperty(null, part, false))
        for (i in 0..20) {
            list.add(SimpleBooleanProperty(null, i.toString(), false))
        }
    }

    /**
     * Sets slider attributes
     *
     * @param slider slider
     * @param tickCount tick count
     * @param blockIncrement block increment
     */
    private fun setSliderAttributes(slider: Slider, tickUnit: Double, tickCount: Int, blockIncrement: Double) {
        slider.majorTickUnit = tickUnit
        slider.minorTickCount = tickCount
        slider.blockIncrement = blockIncrement
        slider.isShowTickMarks = true
        slider.isSnapToTicks = true
        slider.isShowTickLabels = true
    }

    /**
     * Function called when booting up the GUI. Ensures dependencies are installed and
     * specifies the functions to be executed on shutdown.
     */
    override fun onDock() {
        poseApp.initProject()
        currentWindow?.setOnCloseRequest { poseApp.execOnClose() }
    }


    override val root = tabpane {
        //first we have to define some functions that can only be inside the pane

        /**
         * Creates the analysis tab.
         *
         * @return analysis tab
         */
        fun buildAnalysisTab(): Tab {
            return tab("Analysis status") {

                text("Analysis status\n") { style { fontSize = 18.px; fontWeight = FontWeight.BOLD } }

                progressBarsView = listview<HBox> {
                    items.add(hbox(alignment = Pos.TOP_RIGHT) {
                        button("Open general output folder") {
                            action {
                                Runtime.getRuntime().exec("explorer.exe " + outputFolder)
                            }
                        }
                    })
                    items.addAll(getProgressBars(dataFiles))
                }
                progressBarsView.getSelectionModel().clearSelection()
            }
        }

        /**
         * Creates the input tab
         *
         * @return Input tab
         */
        fun buildInputTab(
            keypointsLeft: MutableList<SimpleBooleanProperty>,
            keypointsRight: MutableList<SimpleBooleanProperty>,
            keypointsBody: MutableList<SimpleBooleanProperty>
        ): Tab {

            val tab = tab("Input") {
                borderpane {
                    /**
                     * refreshes keypoint image
                     *
                     * @param side "Right" or "Left" specifying which hand
                     * @param keyImgPane StackPane where the images should be
                     * @param keyImg List of keypoint names
                     */
                    fun refreshImg(side: String, keyImgPane: StackPane, keyImg: MutableList<String>) {
                        keyImgPane.children.clear()
                        val handStream = FileInputStream("res/Keypoint_pics/keypoints_hand${side}.png")
                        keyImgPane.add(imageview(Image(handStream)))
                        for (i in keyImg) {
                            val keypointStream = FileInputStream(i)
                            keyImgPane.add(imageview(Image(keypointStream)) {
                                if (side.equals("Left")) scaleX = -scaleX
                            })
                        }
                    }

                    /**
                     * The function used to update the selected keypoints
                     */
                    fun keypointSelectionAction(
                        keypointsHand: MutableList<SimpleBooleanProperty>,
                        keyPointStart: Int,
                        keyPointMiddle: Int,
                        keyPointEnd: Int,
                        finger: String,
                        keyImages: MutableList<String>,
                        selected:Boolean,
                        side: String
                    ) {
                        keypointsHand.filter {
                            it.name == keyPointStart.toString()
                                    || it.name == keyPointMiddle.toString()
                                    || it.name == keyPointEnd.toString()
                        }.map {
                            it.value = keypointsHand.filter { it.name == finger }[0].value
                        }

                        if (selected)
                            for (k in keyPointStart..keyPointEnd)
                                keyImages.add("res/Keypoint_pics/${k}_mark.png")
                        else
                            for (j in keyPointStart..keyPointEnd)
                                keyImages.removeIf { it == "res/Keypoint_pics/${j}_mark.png" }
                        when (side) {
                            "Left" -> refreshImg(side, keyImgPaneLeft, keyImages)
                            "Right" -> refreshImg(side, keyImgPaneRight, keyImages)
                            else -> throw IllegalArgumentException(
                                "Keypoints have to belong to the Left or Right side"
                            )
                        }
                    }

                    /**
                     * Generates the checkbox tree for keypoints, both left and right
                     *
                     * @param scroll ScrollPane for the keypoints
                     */
                    fun generateCheckboxTree(
                        scroll: ScrollPane,
                        keypointsLeft: MutableList<SimpleBooleanProperty>,
                        keypointsRight: MutableList<SimpleBooleanProperty>,
                        keypointsBody: MutableList<SimpleBooleanProperty>
                    ) {

                        /**
                         * Creates the Keypoints Section
                         *
                         * @return HBox containing keypoints part
                         */
                        fun buildKeypointsSection(
                            keyPointStart: Int,
                            keypointsHand: MutableList<SimpleBooleanProperty>,
                            keyImages: MutableList<String>,
                            side: String
                        ): HBox {
                            val keyPointMiddle = keyPointStart + 1
                            val keyPointEnd = keyPointStart + 2
                            val finger =
                                when (keyPointStart) {
                                    2 -> "Thumb"
                                    6 -> "Index finger"
                                    10 -> "Middle finger"
                                    14 -> "Ring finger"
                                    18 -> "Pinky"
                                    else -> throw IllegalArgumentException(
                                        "keyPointStart has to be a first keypoint of a finger"
                                    )
                                }
                            return hbox(15, alignment = Pos.CENTER_LEFT) {
                                label("")
                                checkbox(
                                    finger,
                                    keypointsHand.filter { it.name == finger }[0]
                                ) {

                                    style {
                                        fontSize = 14.px
                                        fontWeight = FontWeight.BOLD
                                    }

                                    action {
                                        keypointSelectionAction(
                                            keypointsHand,
                                            keyPointStart,
                                            keyPointMiddle,
                                            keyPointEnd,
                                            finger,
                                            keyImages,
                                            this.isSelected,
                                            side
                                        )
                                    }
                                }
                            }
                        }

                        /**
                         * Creates checkbox for hand keypoints
                         *
                         * @param hand contains truth value of hand selection
                         * @param keypointsHand keypoints on the hand
                         * @param keyImages keypoint names
                         * @param side side of the hand
                         *
                         * @return CheckBox for the keypoints
                         */
                        fun handCheckbox(
                            hand: SimpleBooleanProperty,
                            keypointsHand: MutableList<SimpleBooleanProperty>,
                            keyImages: MutableList<String>,
                            side: String
                        ):CheckBox {
                            return checkbox("$side hand", hand) {
                                style {
                                    fontSize = 14.px
                                    fontWeight = FontWeight.EXTRA_BOLD
                                }
                                action {
                                    keypointsHand.map { it.value = hand.value }

                                    if (this.isSelected)
                                        for (k in 0..20)
                                            keyImages.add("res/Keypoint_pics/${k}_mark.png")
                                    else
                                        for (j in 0..20)
                                            keyImages.removeIf { it == "res/Keypoint_pics/${j}_mark.png" }

                                    when (side) {
                                        "Left" -> refreshImg(side, keyImgPaneLeft, keyImages)
                                        "Right" -> refreshImg(side, keyImgPaneRight, keyImages)
                                    }
                                }
                            }
                        }

                        /**
                         * A Function that updates the selection of palm keypoints
                         */
                        fun palmCheckboxButton(
                            keypointsHand: MutableList<SimpleBooleanProperty>,
                            palmPts: List<Int>,
                            keyImages: MutableList<String>,
                            selected: Boolean,
                            side: String
                        ) {
                            keypointsHand.filter {
                                it.name == "0" || it.name == "1" || it.name == "5" || it.name == "9"
                                        || it.name == "13" || it.name == "17"
                            }
                                .map {
                                    it.value = keypointsHand.filter { it.name == "Palm" }[0].value
                                }

                            if (selected) {
                                for (point in palmPts)
                                    keyImages.add("res/Keypoint_pics/${point}_mark.png")
                            } else {
                                for (point in palmPts)
                                    keyImages.removeIf { it == "res/Keypoint_pics/${point}_mark.png" }
                            }
                            when (side) {
                                "Left" -> refreshImg(side, keyImgPaneLeft, keyImages)
                                "Right" -> refreshImg(side, keyImgPaneRight, keyImages)
                            }
                        }

                        /**
                         * Creates checkbox for palm keypoints
                         *
                         * @param keypointsHand truth values of selection of keypoints on the hand
                         * @param palmPts keypoints on the palm
                         * @param keyImages keypoint names
                         * @param side side of the hand
                         *
                         * @return CheckBox for the keypoints
                         */
                        fun palmCheckbox(
                            keypointsHand: MutableList<SimpleBooleanProperty>,
                            palmPts: List<Int>,
                            keyImages: MutableList<String>,
                            side: String
                        ): HBox {
                            return hbox(15, alignment = Pos.CENTER_LEFT) {
                                label("")
                                checkbox("Palm", keypointsHand.filter { it.name == "Palm" }[0]) {
                                    style {
                                        fontSize = 14.px
                                        fontWeight = FontWeight.BOLD
                                    }
                                    action {
                                        palmCheckboxButton(keypointsHand, palmPts, keyImages, this.isSelected, side)
                                    }
                                }
                            }
                        }

                        /**
                         * Creates checkbox for hand keypoints
                         *
                         * @param side side of the hand
                         * @param keypointsHand keypoints on the hand
                         * @param keypointsBody keypoints of the body
                         * @param hand contains truth value of hand selection
                         * @param keyImages keypoint names
                         * @param palmPts keypoints on the palm
                         * @param restPts keypoints not on the palm
                         *
                         * @return CheckBox for the keypoints
                         */
                        fun buildCheckboxHand(
                            side: String,
                            keypointsHand: MutableList<SimpleBooleanProperty>,
                            keypointsBody: MutableList<SimpleBooleanProperty>,
                            hand: SimpleBooleanProperty,
                            keyImages: MutableList<String>,
                            palmPts: List<Int>,
                            restPts: List<Int>
                        ): Node {
                            val col = vbox(10, alignment = Pos.CENTER_LEFT)

                            col.add(checkbox("$side elbow", keypointsBody.filter {
                                it.name == if (side == "Left") "3" else "6"
                            }[0]))

                            col.add(checkbox("$side wrist", keypointsBody.filter {
                                it.name == if (side == "Left") "4" else "7"
                            }[0]))

                            col.add( handCheckbox(hand, keypointsHand, keyImages, side))

                            col.add( palmCheckbox(keypointsHand, palmPts, keyImages, side))


                            for (p in palmPts) {
                                col.add(hbox(15, alignment = Pos.CENTER_LEFT) {
                                    label("");label("")
                                    checkbox("$p", keypointsHand.filter { it.name == p.toString() }[0]) {
                                        action {
                                            if (this.isSelected)
                                                keyImages.add("res/Keypoint_pics/${p}_mark.png")
                                            else
                                                keyImages.remove("res/Keypoint_pics/${p}_mark.png")
                                            when (side) {
                                                "Left" -> refreshImg(side, keyImgPaneLeft, keyImages)
                                                "Right" -> refreshImg(side, keyImgPaneRight, keyImages)
                                            }
                                        }
                                    }
                                })
                            }

                            val startingKeypoints = listOf(2, 6, 10, 14, 18)

                            for (keyP in restPts) {
                                if (startingKeypoints.contains(keyP)) {
                                    col.add(buildKeypointsSection(keyP, keypointsHand, keyImages, side))
                                }

                                col.add(hbox(15, alignment = Pos.CENTER_LEFT) {
                                    label("");label("")
                                    checkbox("$keyP", keypointsHand.filter { it.name == keyP.toString() }[0]) {
                                        action {
                                            if (this.isSelected)
                                                keyImages.add("res/Keypoint_pics/${keyP}_mark.png")
                                            else
                                                keyImages.remove("res/Keypoint_pics/${keyP}_mark.png")
                                            when (side) {
                                                "Left" -> refreshImg(side, keyImgPaneLeft, keyImages)
                                                "Right" -> refreshImg(side, keyImgPaneRight, keyImages)
                                            }
                                        }
                                    }
                                })
                            }
                            return col
                        }


                        //Now we can actually build the whole checkbox tree
                        addKeypoints(keypointsLeft)
                        val leftHand = SimpleBooleanProperty(null, "Left hand", false)

                        addKeypoints(keypointsRight)
                        val rightHand = SimpleBooleanProperty(null, "Right hand", false)

                        keypointsBody.add(SimpleBooleanProperty(null, "3", false))
                        keypointsBody.add(SimpleBooleanProperty(null, "4", false))
                        keypointsBody.add(SimpleBooleanProperty(null, "6", false))
                        keypointsBody.add(SimpleBooleanProperty(null, "7", false))

                        val palmPts = listOf(0, 1, 5, 9, 13, 17)
                        val restPts = listOf(2, 3, 4, 6, 7, 8, 10, 11, 12, 14, 15, 16, 18, 19, 20)

                        scroll.minWidth = 370.0

                        //Fun fact: When you add the two checkbox vboxes directly to the hbox (hbox{...}), you get an empty pane.
                        val row = hbox(50, alignment = Pos.CENTER_LEFT)
                        row.add(
                            buildCheckboxHand(
                                "Left",
                                keypointsLeft,
                                keypointsBody,
                                leftHand,
                                keyImagesLeft,
                                palmPts,
                                restPts
                            )
                        )
                        row.add(
                            buildCheckboxHand(
                                "Right",
                                keypointsRight,
                                keypointsBody,
                                rightHand,
                                keyImagesRight,
                                palmPts,
                                restPts
                            )
                        )
                        scroll.add(row)
                    }

                    //Fixed windowsize, can be changed if needed
                    //TODO make this the min size set at the beginning
                    prefWidth = 1000.0
                    prefHeight = 750.0

                    center = form {

                        fieldset {
                            hbox(10, alignment = Pos.CENTER_LEFT) {
                                //Create *.avi filters for the file selection function
                                val fileAmount = SimpleStringProperty("0")

                                // Simplify this if possible
                                field("Select file(s)") {
                                    style {
                                        fontWeight = FontWeight.BOLD
                                    }
                                    label() {
                                        this.bind(fileAmount)
                                        style {
                                            fontWeight = FontWeight.NORMAL
                                        }
                                    }
                                    label("file(s) selected") {
                                        style {
                                            fontWeight = FontWeight.NORMAL
                                        }
                                    }
                                }
                                val selectionWindow = SelectFragment()
                                button("Manage input file(s)") {
                                    action {
                                        //Opens a small window that allows for file selection, blocks Spudnig until closed
                                        selectionWindow.openModal(block = true)
                                        if (selectionWindow.save)
                                            files = selectionWindow.observableFiles
                                        fileAmount.bind(files.sizeProperty.asString())
                                    }
                                }
                            }

                            field("Reliability threshold") {
                                style {
                                    fontWeight = FontWeight.BOLD
                                }
                                val thresholdSlider = slider(0.0, 1.0, 0.3)
                                setSliderAttributes(thresholdSlider, 0.1, 0, 0.1)
                                thresholdSlider.valueProperty().bindBidirectional(thresholdValue)
                            }

                            field("Minimum number of frames threshold") {
                                style {
                                    fontWeight = FontWeight.BOLD
                                }
                                val minThreshold = slider(0, 10, 4)
                                setSliderAttributes(minThreshold, 1.0, 0, 1.0)
                                minThreshold.valueProperty().bindBidirectional(minThresholdValue)
                            }

                            field("Minimum gap between frames threshold") {
                                style {
                                    fontWeight = FontWeight.BOLD
                                }
                                val gapThreshold = slider(0, 10, 4)
                                setSliderAttributes(gapThreshold, 1.0, 0, 1.0)
                                gapThreshold.valueProperty().bindBidirectional(gapThresholdValue)
                            }

                            text("Key points") {
                                style {
                                    fontWeight = FontWeight.BOLD
                                }
                            }
                            hbox(10, alignment = Pos.CENTER_LEFT) {

                                val scroller = scrollpane()
                                scroller.isPannable = true
                                generateCheckboxTree(scroller, keypointsLeft, keypointsRight, keypointsBody)

                                keyImgPaneLeft = stackpane() {
                                    val leftStream = FileInputStream("res/Keypoint_pics/keypoints_handLeft.png")
                                    imageview(Image(leftStream))
                                }
                                keyImgPaneRight = stackpane() {
                                    val rightStream = FileInputStream("res/Keypoint_pics/keypoints_handRight.png")
                                    imageview(Image(rightStream))
                                }
                            }

                            vbox(alignment = Pos.CENTER_LEFT) {
                                //Set default directory to the first path of the first selected file
                                var directory = File(System.getProperty("user.dir"))
                                var text: StringProperty = SimpleStringProperty()
                                text.value = directory.absolutePath
                                val directoryChooser = DirectoryChooser()
                                directoryChooser.setTitle("Select output folder")
                                directoryChooser.setInitialDirectory(directory)

                                field("Output destination") {
                                    style {
                                        fontWeight = FontWeight.BOLD
                                    }
                                }

                                val folderStream = FileInputStream("res/folderIcon.png")

                                hbox(10, alignment = Pos.CENTER_LEFT) {
                                    button("", graphic = ImageView(Image(folderStream))) {

                                        action {
                                            // Click this button to go to file destination
                                            //Set directory to directory chosen
                                            var chosenDir = directory
                                            var tempChosenDir = directoryChooser.showDialog(currentStage)
                                            if (tempChosenDir != null)
                                                chosenDir = tempChosenDir
                                            directory = chosenDir
                                            text.value = directory.absolutePath
                                            outputFolder = directory
                                            //If you want to keep the program directory as the starting directory in the file window
                                            //Then comment the next line out
                                            directoryChooser.setInitialDirectory(directory)
                                        }
                                    }
                                    //Label to display the current output directory
                                    label {
                                        this.textProperty().bind(text)
                                    }
                                }
                            }

                            field("File type") {
                                style {
                                    fontWeight = FontWeight.BOLD
                                }
                            }

                            hbox(10, alignment = Pos.CENTER_LEFT) {
                                radiobutton(".CSV", toggleFormat) {
                                    action {
                                        fileFormat = FileType.CSV
                                    }
                                    isSelected = true
                                }
                                radiobutton(".EAF", toggleFormat) {
                                    action {
                                        fileFormat = FileType.EAF
                                    }
                                }
                                radiobutton(".JSON", toggleFormat) {
                                    action {
                                        fileFormat = FileType.JSON
                                    }
                                }
                            }
                        }

                        hbox(10, alignment = Pos.CENTER_RIGHT) {
                            button("Analyze") {
                                action {
                                    val filesInUse = checkIfFilesInUse()

                                    if ( ! filesInUse) {
                                        raiseError(
                                            "One or more of the selected files are already being analyzed",
                                            "Please wait until the current analysis of the file " +
                                                    "is complete or remove the file from your selection"
                                        )
                                    } else {
                                        startAnalysis(tabPane)
                                    }
                                }
                            }
                        }
                    }
                    style {
                    }
                }
            }
            return tab
        }
        buildInputTab( poseApp.getKPL(), poseApp.getKPR(), poseApp.getKPB())
        tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        runLater { currentStage?.focusedProperty()?.addListener(poseApp.getFocusListener()) }
        buildAnalysisTab()

    }

    private fun startAnalysis(tabPane: TabPane) {
        if (poseApp.analyze(files, getNewProgressBars(files.size))){
            progressBarsView.items.addAll(getProgressBars(poseApp.getDataFiles()))
            progressBarHelper.initProgressBars()

            //switch to tab appropriate while / after analyzing
            tabPane.getSelectionModel().select(1)
            println("Analyzing")
        } else {
            tabPaneFix()
        }
    }

    private fun checkIfFilesInUse(): Boolean {
        //check if one or more of the selected files is already being analyzed
        // if it is: do not start the analysis and warn the user
        // that one file can only be analyzed once at a time
        val progressBars = progressBarHelper.getProgressBars()
        val filePathsInUse = progressBars.map{ it -> it.first }
        val filePathsToAdd = files.map { it -> it.invariantSeparatorsPath }
        for (path in filePathsToAdd){
            if (filePathsInUse.contains(path)){
                return false
            }
        }
        return true
    }


    //TODO finish this javadoc
    /**
     * Takes all data files and returns a list containing a progress bar per analysis
     * paired with a string representing the path of the analyzed file
     * (so it can be identified)
     * @param dataFiles file where all data concerning analysis is stored
     */
    fun getProgressBars(dataFiles: HashMap<String, DataFile>): List<HBox> {
        val progressBars = progressBarHelper.getProgressBars()
        return progressBars
            .filter {
                files.map {
                    it.invariantSeparatorsPath
                }.contains(
                    it.first
                )
            }
            .map {
                val box = hbox(10, alignment = Pos.CENTER_LEFT)
                box.add(text(it.first))
                val dataFile = dataFiles.get(it.first)!!
                if (!dataFile.hasError()) {
                    box.add(it.second)
                    box.add(button("Velocity plot") {
                        action {
                            style = "-fx-background-color: Grey"
                            var directory = File(System.getProperty("user.dir"))
                            val dirPicker = DirectoryChooser()
                            dirPicker.setTitle("Select output folder")
                            dirPicker.setInitialDirectory(directory)
                            val chosenDir = dirPicker.showDialog(currentStage)
                            try {
                                poseApp.vPlt(dataFile, chosenDir.canonicalPath)
                                information("Velocity plot generation completed!")
                            } catch (e: Exception) {
                                //TODO warning pop up that you probably tried to generate
                                // a vPlot of files that were already deleted (-> do not exist)
                            }
                        }
                    })
                } else {
                    box.add(text("There was an error analyzing this file :-/"))
                }
                box
            }
    }

    /**
     * The following section takes care of direct communication with the user through pop-ups and the taskbar
     */

    /**
     * Sets new progress for progressbar corresponding to filepath
     */
    fun updateProgressBars(filepath: String, newProgress: Double) {
        progressBarHelper.updateProgressBars(filepath, newProgress)
    }

    /**
     * Raises an error related to spudnig
     */
    fun spudnigError(message: String) {
        raiseError(header="Error in spudnig", content=message, actionFn={})
    }

    /**
     * Raises an error in the GUI
     */
    fun raiseError(
        header: String,
        content: String,
        actionFn: Alert.(ButtonType) -> Unit = {},
        owner: Window? = PoseView().currentWindow) {
        runLater {
            alert(
                Alert.AlertType.ERROR,
                header = header,
                content = content,
                actionFn = actionFn,
                owner = owner
            )
        }
    }

    /**
     * Raises an analyzing error in the analysis tab of the GUI at one filepath.
     *
     * @param filepath file in which the error occurred
     */
    fun raiseAnalyzingError(filepath: String) {
        if (filepath.isNotEmpty()) {
            //make error shown in view
            var index = progressBarHelper.getIndexForPath(filepath)
            index++ //the very first item in progressBarsView the button to open the output location -> offset 1
            progressBarsView.items.removeAt(index)
            val errorBox = hbox(10, alignment = Pos.CENTER_LEFT)
            errorBox.add(text(filepath))
            errorBox.add(text("There was an error analyzing this file :-/"))
            progressBarsView.items.add(index, errorBox)
            tabPaneFix()
        }
        //make taskbar react
        progressBarHelper.showErrorProgress()
    }

    /**
     * Removes the analyzing error from the GUI.
     */
    fun removeAnalyzingError() {
        progressBarHelper.updateTaskbarProgressbar()
    }


    /**
     * Getters and Setters
     */

    /**
     * Get ProgressBarHelper. We need this getter here because ProgressBars are part of javafx
     * and have to be initialized in a View thread.
     */
    fun getProgressBarHelper(): ProgressBarHelper {
        return progressBarHelper
    }

    fun getNewProgressBars(n: Int): List<ProgressBar> {
        var progressBars: MutableList<ProgressBar> = mutableListOf()
        for (i in 0..n) {
            progressBars.add(0, progressbar(SimpleDoubleProperty(0.0)) {
                scaleX = 1.0
                scaleY = 1.0
            })
        }
        return progressBars
    }

}