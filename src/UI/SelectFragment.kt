package src.UI

import PoseView
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.control.SelectionMode
import javafx.stage.FileChooser
import tornadofx.*
import java.io.File

class SelectFragment : Fragment("File Selection") {
    val oldFiles: List<File> = emptyList<File>()//by param()
    var observableFiles: ObservableList<File> = FXCollections.observableArrayList()
    var removalIndices: ObservableList<Int> = FXCollections.observableArrayList()
    var save: Boolean = true


    override val root = borderpane {
        prefHeight = 400.0
        prefWidth = 400.0

        //Create the filters
        val filters = createFilters()
        top {
            hbox {
                button("Add") {
                    action {
                        //Select one or multiple *.avi files and returns the files list that contains all the files
                        addFiles(filters)
                    }
                }
                button("Remove") {
                    action {
                        //Removes all files that are selected in the listView when the button is pressed
                        for (i in removalIndices.reversed()) {
                            observableFiles.removeAt(i)
                        }
                    }
                }

                button("Clear") {
                    action {
                        // Removes all the files
                        observableFiles.clear()
                    }
                }
            }
        }
        center {
            listview(observableFiles) {
                removalIndices = selectionModel.selectedIndices
                selectionModel.selectionMode = SelectionMode.MULTIPLE
                //Small context menu, although I have never seen anyone use this during the entirety of this project
                contextmenu {
                    //Opens file selection window
                    item("Add") {
                        action {
                            addFiles(filters)
                        }
                    }
                    //Remove the file from the selection
                    item("Remove") {
                        action {
                            val removeIndex = this@listview.selectionModel.selectedIndex
                            observableFiles.removeAt(removeIndex)
                        }
                    }
                }
            }
        }
        bottom {
            hbox {
                alignment = Pos.CENTER_RIGHT
                button("Done") {
                    //Exit file selection; save selected files and return them to PoseView
                    action {
                        save = true
                        close()
                    }
                }
                button("Cancel") {
                    //Cancel file selection; no changes are made to selected files
                    action {
                        save = false
                        close()
                    }
                }
            }
        }
        //this.maxHeight = 75.0 + 75.0 * observableFiles.size
    }

    /**
     * Creates the filters used in the file selection to force the selection of a specific file type. To allow more
     * files to be selected, add more extensions.
     */
    private fun createFilters(): Array<FileChooser.ExtensionFilter> {
        val fileLocs = SimpleStringProperty()
        val fileChooser = FileChooser.ExtensionFilter(
            "Videos",
            "*.avi"    //If you want to allow more filetypes, you can add them here
        )
        //add video formats
        val filters = arrayOf(fileChooser)
        return filters
    }

    /**
     * Opens a window where you can select one or multiple files, according to the filters specified in the
     * filters parameter
     */
    private fun addFiles(filters: Array<FileChooser.ExtensionFilter>) {
        val newFiles = chooseFile(filters = filters, mode = FileChooserMode.Multi)
        val selectedFiles = newFiles.partition {
            it.absolutePath.contains(" ")
        }
        val permittedFiles = selectedFiles.second
        println(permittedFiles.size.toString() + " files selected")
        observableFiles.addAll(permittedFiles)
        println(observableFiles.size.toString() + " files in total")
        val refusedFiles = selectedFiles.first

        // Checks if there is a space in any of the video paths, removes these and shows an infomessage
        if (refusedFiles.isNotEmpty()){
            println(refusedFiles.toString() + "refused because of spaces in path/name")
            information(
                "One or more files you tried to add have spaces in their name or path!",
                "The files with spaces have been ignored.\n" + "One of them is: " + refusedFiles.first(),
                title ="Space in the video path",
                owner = PoseView().currentWindow)
        }
    }
}
