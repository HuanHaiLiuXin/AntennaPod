package de.danoeh.antennapod.ui.screen.download

import androidx.annotation.StringRes
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.download.DownloadError

/**
 * Provides user-visible labels for download errors.
 */
class DownloadErrorLabel {
    companion object {
        @StringRes
        @JvmStatic
        fun from(error: DownloadError): Int {
            return when (error) {
                DownloadError.SUCCESS -> R.string.download_successful
                DownloadError.ERROR_PARSER_EXCEPTION -> R.string.download_error_parser_exception
                DownloadError.ERROR_UNSUPPORTED_TYPE -> R.string.download_error_unsupported_type
                DownloadError.ERROR_CONNECTION_ERROR -> R.string.download_error_connection_error
                DownloadError.ERROR_MALFORMED_URL -> R.string.download_error_error_unknown
                DownloadError.ERROR_IO_ERROR -> R.string.download_error_io_error
                DownloadError.ERROR_FILE_EXISTS -> R.string.download_error_error_unknown
                DownloadError.ERROR_DOWNLOAD_CANCELLED -> R.string.download_canceled_msg
                DownloadError.ERROR_DEVICE_NOT_FOUND -> R.string.download_error_device_not_found
                DownloadError.ERROR_HTTP_DATA_ERROR -> R.string.download_error_http_data_error
                DownloadError.ERROR_NOT_ENOUGH_SPACE -> R.string.download_error_insufficient_space
                DownloadError.ERROR_UNKNOWN_HOST -> R.string.download_error_unknown_host
                DownloadError.ERROR_REQUEST_ERROR -> R.string.download_error_request_error
                DownloadError.ERROR_DB_ACCESS_ERROR -> R.string.download_error_db_access
                DownloadError.ERROR_UNAUTHORIZED -> R.string.download_error_unauthorized
                DownloadError.ERROR_FILE_TYPE -> R.string.download_error_file_type_type
                DownloadError.ERROR_FORBIDDEN -> R.string.download_error_forbidden
                DownloadError.ERROR_IO_WRONG_SIZE -> R.string.download_error_wrong_size
                DownloadError.ERROR_IO_BLOCKED -> R.string.download_error_blocked
                DownloadError.ERROR_UNSUPPORTED_TYPE_HTML -> R.string.download_error_unsupported_type_html
                DownloadError.ERROR_NOT_FOUND -> R.string.download_error_not_found
                DownloadError.ERROR_CERTIFICATE -> R.string.download_error_certificate
                DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE -> R.string.download_error_parser_exception
                else -> {
                    if (BuildConfig.DEBUG) {
                        throw IllegalArgumentException("No mapping from download error to label")
                    }
                    R.string.download_error_error_unknown
                }
            }
        }
    }
}
