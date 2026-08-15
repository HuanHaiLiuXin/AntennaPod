package de.danoeh.antennapod.parser.media

import java.io.IOException
import java.io.InputStream

class MediaFormatDetector private constructor() {

    enum class Format {
        ID3, OGG, M4A, UNKNOWN
    }

    class Result internal constructor(@JvmField val format: Format, @JvmField val bytes: ByteArray)

    companion object {
        private const val PREFIX_READ_LIMIT = 64

        @JvmStatic
        fun detect(input: InputStream): Result {
            var prefix = ByteArray(PREFIX_READ_LIMIT)
            val bytesRead = input.read(prefix)
            return if (bytesRead > 0) {
                prefix = prefix.copyOf(bytesRead)
                Result(detectFormat(prefix), prefix)
            } else {
                Result(Format.UNKNOWN, ByteArray(0))
            }
        }

        @JvmStatic
        fun detectFormat(prefix: ByteArray): Format {
            if (prefix.size >= 3
                    && prefix[0].toInt() == 0x49 && prefix[1].toInt() == 0x44 && prefix[2].toInt() == 0x33) {
                return Format.ID3
            } else if (prefix.size >= 4
                    && prefix[0].toInt() == 0x4F && prefix[1].toInt() == 0x67
                    && prefix[2].toInt() == 0x67 && prefix[3].toInt() == 0x53) {
                return Format.OGG
            } else if (prefix.size >= 8
                    && prefix[4].toInt() == 0x66 && prefix[5].toInt() == 0x74
                    && prefix[6].toInt() == 0x79 && prefix[7].toInt() == 0x70) {
                return Format.M4A
            }
            return Format.UNKNOWN
        }
    }
}
