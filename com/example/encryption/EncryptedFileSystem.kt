package com.example.encryption

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import java.io.*
import java.security.SecureRandom
import java.util.*

class EncryptedFileSystem {
    private var key: SecretKeySpec? = null

    companion object {
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 16
        private const val KEY_LENGTH = 256
        private const val ITERATION_COUNT = 100000
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    }

    fun generateKey(keyPath: String, password: String) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).apply { random.nextBytes(this) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val keyBytes = factory.generateSecret(spec).encoded
        this.key = SecretKeySpec(keyBytes, "AES")

        FileOutputStream(keyPath).use { fos ->
            DataOutputStream(fos).use { dos ->
                dos.writeInt(SALT_LENGTH)
                dos.write(salt)
                dos.writeInt(keyBytes.size)
                dos.write(keyBytes)
            }
        }
    }

    fun loadKey(keyPath: String, password: String) {
        FileInputStream(keyPath).use { fis ->
            DataInputStream(fis).use { dis ->
                val saltLength = dis.readInt()
                if (saltLength != SALT_LENGTH) throw Exception("잘못된 키 파일 형식")
                val salt = ByteArray(saltLength).apply { dis.readFully(this) }

                val keyLength = dis.readInt()
                val storedKey = ByteArray(keyLength).apply { dis.readFully(this) }

                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
                val generatedKey = factory.generateSecret(spec).encoded

                if (!generatedKey.contentEquals(storedKey)) throw Exception("잘못된 비밀번호")
                this.key = SecretKeySpec(generatedKey, "AES")
            }
        }
    }

    fun encryptFile(filePath: String, chunkSize: Int): String {
        key ?: throw Exception("키가 로드되지 않음")

        val encryptedFilePath = "$filePath.lock"
        val random = SecureRandom()
        val iv = ByteArray(IV_LENGTH).apply { random.nextBytes(this) }

        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        }

        FileInputStream(filePath).use { fis ->
            FileOutputStream(encryptedFilePath).use { fos ->
                DataOutputStream(fos).use { dos ->
                    dos.writeInt(chunkSize)
                    dos.write(iv)

                    val buffer = ByteArray(chunkSize)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cipher.update(buffer, 0, bytesRead)?.let { encryptedChunk ->
                            dos.writeInt(encryptedChunk.size)
                            dos.write(encryptedChunk)
                        }
                    }
                    cipher.doFinal()?.let { finalChunk ->
                        dos.writeInt(finalChunk.size)
                        dos.write(finalChunk)
                    }
                }
            }
        }

        return encryptedFilePath
    }

    fun decryptFile(encryptedFilePath: String): String =
        decryptFile(encryptedFilePath, encryptedFilePath.dropLast(5))

    fun decryptFile(encryptedFilePath: String, outputPath: String): String {
        key ?: throw Exception("키가 로드되지 않음")

        FileInputStream(encryptedFilePath).use { fis ->
            DataInputStream(fis).use { dis ->
                FileOutputStream(outputPath).use { fos ->
                    val chunkSize = dis.readInt()
                    val iv = ByteArray(IV_LENGTH).apply { dis.readFully(this) }

                    val cipher = Cipher.getInstance(ALGORITHM).apply {
                        init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                    }

                    while (dis.available() > 0) {
                        val encryptedChunkLength = dis.readInt()
                        val encryptedChunk = ByteArray(encryptedChunkLength).apply { dis.readFully(this) }

                        cipher.update(encryptedChunk)?.let { decryptedChunk ->
                            fos.write(decryptedChunk)
                        }
                    }
                    cipher.doFinal()?.let { finalChunk ->
                        fos.write(finalChunk)
                    }
                }
            }
        }

        File(encryptedFilePath).delete()
        return outputPath
    }

    private fun secureDelete(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return

        val length = file.length()
        RandomAccessFile(file, "rw").use { raf ->
            val randomData = ByteArray(1024)
            val random = SecureRandom()
            var written = 0L
            while (written < length) {
                random.nextBytes(randomData)
                val toWrite = minOf(1024, (length - written).toInt())
                raf.write(randomData, 0, toWrite)
                written += toWrite
            }
        }
        file.delete()
    }
}