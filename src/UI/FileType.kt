package src.UI

/**
 * When choosing a filetype ( as output for example ) the user does not have free choice, as we only support
 * a certain number of filetypes.
 * This class facilitates easier validation prettier handling of commands.
 */
enum class FileType(val type: String) {
    CSV(".csv"),
    JSON(".json"),
    EAF(".eaf")
}

