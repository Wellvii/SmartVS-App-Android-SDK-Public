package com.vvvital.vital_smartvs.log

import android.util.Log


internal object VSLog {
    private const val MSG_PREFIX = "[SmartVSLog] "
    private const val TAG_PREFIX = ""

    fun printStackTrace(Tag: String, e: Exception?) {
        if (VSGlobals.isTest) {
            if (e != null && e.stackTrace != null) {
                var sb = StringBuilder()
                val stackTraceElements = e.stackTrace

                if (e.toString() != null) {
                    VSLog.e(
                        Tag, StringBuilder().append("------ ").append(e.toString()).append(" -----")
                            .toString()
                    )
                }

                if (e.cause != null) {
                    val cause = e.cause
                    val causeErrorMessage = cause!!.message
                    VSLog.e(
                        Tag,
                        StringBuilder().append("------ ").append(causeErrorMessage).append(" -----")
                            .toString()
                    )
                }

                if (e.message != null) {
                    VSLog.e(
                        Tag, StringBuilder().append("------ ").append(e.message).append(" -----")
                            .toString()
                    )
                }

                for (traceElement in stackTraceElements) {
                    sb.append(traceElement.className).append(" - ").append(traceElement.methodName)
                        .append(" - ").append(traceElement.lineNumber)

                    VSLog.e(Tag, sb.toString())
                    sb = StringBuilder()
                }
            } else {

            }
        }
    }


    fun v(tag: String, message: String) {
        if (VSGlobals.isTest) {
            Log.v(TAG_PREFIX + tag, MSG_PREFIX + message)
        }
    }

    fun d(tag: String, message: String) {
        if (VSGlobals.isTest) {
            Log.d(TAG_PREFIX + tag, MSG_PREFIX + message)
        }
    }

    fun i(tag: String, message: String) {
        if (VSGlobals.isTest) {
            Log.i(TAG_PREFIX + tag, MSG_PREFIX + message)
        }
    }

    fun w(tag: String, message: String) {
        if (VSGlobals.isTest) {
            Log.w(TAG_PREFIX + tag, MSG_PREFIX + message)
        }
    }

    fun e(tag: String, message: String) {
        if (VSGlobals.isTest) {
            Log.e(TAG_PREFIX + tag, MSG_PREFIX + message)
        }
    }

    // Message plus throwables
    fun v(tag: String, message: String, tr: Throwable) {
        if (VSGlobals.isTest) {
            Log.v(TAG_PREFIX + tag, MSG_PREFIX + message, tr)
        }
    }

    fun d(tag: String, message: String, tr: Throwable) {
        if (VSGlobals.isTest) {
            Log.d(TAG_PREFIX + tag, MSG_PREFIX + message, tr)
        }
    }

    fun i(tag: String, message: String, tr: Throwable) {
        if (VSGlobals.isTest) {
            Log.i(TAG_PREFIX + tag, MSG_PREFIX + message, tr)
        }
    }

    fun w(tag: String, message: String, tr: Throwable) {
        if (VSGlobals.isTest) {
            Log.w(TAG_PREFIX + tag, MSG_PREFIX + message, tr)
        }
    }

    fun e(tag: String, message: String, tr: Throwable) {
        if (VSGlobals.isTest) {
            Log.e(TAG_PREFIX + tag, MSG_PREFIX + message, tr)
        }
    }
}