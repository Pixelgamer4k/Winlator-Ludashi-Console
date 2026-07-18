package com.winlator.cmod.console.compat

import android.content.Context
import com.winlator.cmod.console.compat.LaunchFailureClassifier.RetryAction

/**
 * Persists the last game-session exit tip across AppUtils.restartApplication(),
 * and keeps a durable copy (with log excerpt) for the Hive Agent.
 */
object SessionExit {

    private const val PREFS = "console_session_exit"
    private const val KEY_PENDING = "pending"
    private const val KEY_IS_ERROR = "is_error"
    private const val KEY_TITLE = "title"
    private const val KEY_MESSAGE = "message"
    private const val KEY_RETRY = "retry_action"
    private const val KEY_SHORTCUT_PATH = "shortcut_path"
    private const val KEY_CONTAINER_ID = "container_id"
    private const val KEY_SHORTCUT_NAME = "shortcut_name"

    private const val KEY_LAST_IS_ERROR = "last_is_error"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_LAST_MESSAGE = "last_message"
    private const val KEY_LAST_RETRY = "last_retry_action"
    private const val KEY_LAST_SHORTCUT_PATH = "last_shortcut_path"
    private const val KEY_LAST_CONTAINER_ID = "last_container_id"
    private const val KEY_LAST_SHORTCUT_NAME = "last_shortcut_name"
    private const val KEY_LAST_LOG = "last_log"
    private const val KEY_LAST_AT = "last_at"

    data class Pending(
        val isError: Boolean,
        val title: String,
        val message: String,
        val retryAction: RetryAction,
        val shortcutPath: String?,
        val containerId: Int,
        val shortcutName: String?,
    )

    data class LastSession(
        val isError: Boolean,
        val title: String,
        val message: String,
        val retryAction: RetryAction,
        val shortcutPath: String?,
        val containerId: Int,
        val shortcutName: String?,
        val logExcerpt: String,
        val atMs: Long,
    )

    fun store(
        context: Context,
        verdict: LaunchFailureClassifier.Verdict,
        shortcutPath: String?,
        containerId: Int,
        shortcutName: String?,
    ) {
        val logExcerpt = try {
            LaunchLogBuffer.snapshotText(12_000)
        } catch (_: Exception) {
            ""
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
            .putBoolean(KEY_LAST_IS_ERROR, verdict.isError)
            .putString(KEY_LAST_TITLE, verdict.title)
            .putString(KEY_LAST_MESSAGE, verdict.message)
            .putString(KEY_LAST_RETRY, verdict.retryAction.name)
            .putString(KEY_LAST_SHORTCUT_PATH, shortcutPath)
            .putInt(KEY_LAST_CONTAINER_ID, containerId)
            .putString(KEY_LAST_SHORTCUT_NAME, shortcutName)
            .putString(KEY_LAST_LOG, logExcerpt)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())

        if (verdict.isError) {
            edit.putBoolean(KEY_PENDING, true)
                .putBoolean(KEY_IS_ERROR, true)
                .putString(KEY_TITLE, verdict.title)
                .putString(KEY_MESSAGE, verdict.message)
                .putString(KEY_RETRY, verdict.retryAction.name)
                .putString(KEY_SHORTCUT_PATH, shortcutPath)
                .putInt(KEY_CONTAINER_ID, containerId)
                .putString(KEY_SHORTCUT_NAME, shortcutName)
        } else {
            // Drop UI error card only; keep last_* for the agent.
            edit.putBoolean(KEY_PENDING, false)
        }
        edit.apply()
    }

    fun consume(context: Context): Pending? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return null
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        val message = prefs.getString(KEY_MESSAGE, null) ?: return null
        val pending = Pending(
            isError = prefs.getBoolean(KEY_IS_ERROR, true),
            title = title,
            message = message,
            retryAction = parseRetry(prefs.getString(KEY_RETRY, null)),
            shortcutPath = prefs.getString(KEY_SHORTCUT_PATH, null),
            containerId = prefs.getInt(KEY_CONTAINER_ID, 0),
            shortcutName = prefs.getString(KEY_SHORTCUT_NAME, null),
        )
        clearPending(context)
        return pending
    }

    /** Last finished session (error or not), including log excerpt — for Hive Agent. */
    fun lastSession(context: Context): LastSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_LAST_TITLE, null) ?: return null
        val message = prefs.getString(KEY_LAST_MESSAGE, null) ?: return null
        return LastSession(
            isError = prefs.getBoolean(KEY_LAST_IS_ERROR, false),
            title = title,
            message = message,
            retryAction = parseRetry(prefs.getString(KEY_LAST_RETRY, null)),
            shortcutPath = prefs.getString(KEY_LAST_SHORTCUT_PATH, null),
            containerId = prefs.getInt(KEY_LAST_CONTAINER_ID, 0),
            shortcutName = prefs.getString(KEY_LAST_SHORTCUT_NAME, null),
            logExcerpt = prefs.getString(KEY_LAST_LOG, "").orEmpty(),
            atMs = prefs.getLong(KEY_LAST_AT, 0L),
        )
    }

    fun clear(context: Context) {
        clearPending(context)
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING, false)
            .apply()
    }

    private fun parseRetry(raw: String?): RetryAction {
        return try {
            RetryAction.valueOf(raw ?: RetryAction.NONE.name)
        } catch (_: Exception) {
            RetryAction.NONE
        }
    }
}
