package anhiutangerinee.kittisu.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CredentialCodec {
    private const val PREFIX = "kittisu-pbkdf2-sha256"
    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(credential: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(credential, salt, ITERATIONS)
        return try {
            listOf(
                PREFIX,
                FORMAT_VERSION.toString(),
                ITERATIONS.toString(),
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derived),
            ).joinToString("$")
        } finally {
            derived.fill(0)
        }
    }

    fun verify(credential: CharArray, encoded: String): Boolean {
        val parsed = parse(encoded) ?: return false
        val candidate = runCatching { derive(credential, parsed.salt, parsed.iterations) }
            .getOrNull() ?: return false
        return try {
            MessageDigest.isEqual(candidate, parsed.hash)
        } finally {
            candidate.fill(0)
        }
    }

    internal fun isSupported(encoded: String): Boolean = parse(encoded) != null

    private fun derive(credential: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(credential, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun parse(encoded: String): ParsedCredential? = runCatching {
        val parts = encoded.split('$')
        require(parts.size == 5)
        require(parts[0] == PREFIX)
        require(parts[1].toInt() == FORMAT_VERSION)
        val iterations = parts[2].toInt()
        require(iterations == ITERATIONS)
        val salt = Base64.getDecoder().decode(parts[3])
        val hash = Base64.getDecoder().decode(parts[4])
        require(salt.size == SALT_BYTES)
        require(hash.size * 8 == KEY_BITS)
        ParsedCredential(iterations, salt, hash)
    }.getOrNull()

    private data class ParsedCredential(
        val iterations: Int,
        val salt: ByteArray,
        val hash: ByteArray,
    )
}
