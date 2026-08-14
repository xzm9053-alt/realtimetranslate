package com.xzm.realtimetranslate.data

/**
 * What to do with a finished session's transcripts.
 */
enum class HistoryMode {
    /** Persist every finished session, but keep at most [com.xzm.realtimetranslate.data.UserSettings.historyLimit]
     *  entries — the oldest are auto-dropped when the cap is exceeded. */
    AUTO_CLEAR,

    /** Persist every finished session forever; history is never auto-deleted. */
    SAVE_ALL,
    ;

    companion object {
        fun fromStorage(raw: String?): HistoryMode =
            entries.firstOrNull { it.name == raw } ?: SAVE_ALL
    }
}
