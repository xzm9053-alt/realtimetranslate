package com.xzm.realtimetranslate.data

/**
 * What to do with a finished session's transcripts.
 */
enum class HistoryMode {
    /** Don't write the session into the history file (only the last-session
     *  preview/export on the subtitle screen stays available in-memory). */
    AUTO_CLEAR,

    /** Append every finished session to the persistent history. */
    SAVE_ALL,
    ;

    companion object {
        fun fromStorage(raw: String?): HistoryMode =
            entries.firstOrNull { it.name == raw } ?: SAVE_ALL
    }
}
