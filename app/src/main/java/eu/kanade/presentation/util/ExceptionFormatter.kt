package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

context(context: Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            // KMK -->
            is HttpException -> return when (code) {
                401, 403 -> context.stringResource(KMR.strings.error_plain_blocked, code)
                404, 410 -> context.stringResource(KMR.strings.error_plain_not_found, code)
                429 -> context.stringResource(KMR.strings.error_plain_rate_limited, code)
                in 500..599 -> context.stringResource(KMR.strings.error_plain_server, code)
                else -> context.stringResource(MR.strings.exception_http, code)
            }
            is SocketTimeoutException -> return context.stringResource(KMR.strings.error_plain_timeout)
            is SSLException -> return context.stringResource(KMR.strings.error_plain_ssl)
            is ConnectException -> return context.stringResource(KMR.strings.error_plain_connect)
            // KMK <--
            is UnknownHostException -> {
                return if (!context.isOnline()) {
                    context.stringResource(MR.strings.exception_offline)
                } else {
                    // KMK -->
                    context.stringResource(KMR.strings.error_plain_unknown_host)
                    // KMK <--
                }
            }

            is NoResultsException -> return context.stringResource(MR.strings.no_results_found)
            is SourceNotInstalledException -> return context.stringResource(MR.strings.loader_not_implemented_error)
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
