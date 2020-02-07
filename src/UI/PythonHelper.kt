package src.UI

import java.io.BufferedReader
import java.io.InputStreamReader

class PythonHelper {

    private lateinit var pythonInstalledResponse:String

    /**
     * Checks the installed (or the standard) python version
     * and raises an error if python is not available or the version is not supported.
     *
     * @return whether a supported python version is installed
     */
    fun checkSupportedPythonInstalled(): Boolean {
        val p = Runtime.getRuntime().exec("python -V")
        val output = BufferedReader(InputStreamReader(p.inputStream))
        val response = output.readLines().joinToString("\n")
        pythonInstalledResponse = response
        return response.matches(Regex("""Python 3\.\d+\.\d+"""))
    }

    /**
     * Getter for the response when running "python -V" in the commandline
     */
    fun getPythonInstalledResponse():String {
        if (pythonInstalledResponse.isNullOrEmpty()){
            checkSupportedPythonInstalled()
        }
        return pythonInstalledResponse
    }

    /**
     * Installs dependencies for spudnig: pip; cv2; pandas
     */
    fun installDependencies() {
        updatePip()
        installPythonPackageCV2()
        installPythonPackagePandas()
    }

    /**
     * Updates pip
     */
    fun updatePip() {
        Runtime.getRuntime().exec("python -m pip install –upgrade pip")
    }

    /**
     * Installs CV2
     */
    fun installPythonPackageCV2() {
        Runtime.getRuntime().exec("pip install opencv-python")
    }

    /**
     * Installs pandas
     */
    fun installPythonPackagePandas() {
        Runtime.getRuntime().exec("pip install pandas")
    }
}