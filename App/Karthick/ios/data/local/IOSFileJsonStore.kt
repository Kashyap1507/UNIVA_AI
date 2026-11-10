package com.kashyap.wizlyai.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** [LocalJsonStore] as files in Application Support/wizly_json. */
@OptIn(ExperimentalForeignApi::class)
class IOSFileJsonStore : LocalJsonStore {
    private val dir: String =
        (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String)
            .orEmpty() + "/wizly_json"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }

    private fun path(name: String) = "$dir/$name"

    override fun read(name: String): String? =
        if (NSFileManager.defaultManager.fileExistsAtPath(path(name))) {
            NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null)
        } else {
            null
        }

    override fun write(
        name: String,
        text: String,
    ) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        (text as NSString).writeToFile(path(name), atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtPath(path(name), error = null)
    }

    override fun clear() {
        NSFileManager.defaultManager.removeItemAtPath(dir, error = null)
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }
}
