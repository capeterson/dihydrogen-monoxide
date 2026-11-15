package casa.peterson.dhmo.core.backup

enum class BackupError { NOT_A_BACKUP, CORRUPT, NEWER_VERSION, PASSPHRASE_REQUIRED, WRONG_PASSPHRASE, IO, AUTH, QUOTA, NETWORK }

/** The only exception [BackupCodec] and [BackupCrypto] throw for bad input. */
class BackupException(val error: BackupError, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: error.name, cause)
