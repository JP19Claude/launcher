package dev.hearth.launcher.data

import java.security.MessageDigest

/**
 * Projekt Zenith is hidden in the Illuminati behind a secret password. Only its SHA-256 is kept
 * here; what's typed is compared without regard to case and spaces.
 */
object ProjectZenithLock {
    private const val HASH = "00b90115cea6a1e6d536739f5c8747d8fd81ba861574f1e0ad954cc98cd69984"

    fun opens(input: String): Boolean {
        val typed = input.filterNot { it.isWhitespace() }.uppercase(java.util.Locale.ROOT)
        if (typed.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256").digest(typed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } == HASH
    }
}
