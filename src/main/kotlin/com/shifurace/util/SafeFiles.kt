package com.shifurace.util

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object SafeFiles {
    /** Grava em um .tmp e troca de uma vez, para não corromper o arquivo se o servidor cair no meio. */
    fun writeAtomic(target: Path, content: String) {
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(tmp, content)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
