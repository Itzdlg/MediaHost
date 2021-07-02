package me.schooltests.mediahost.sql.columns

import org.jetbrains.exposed.sql.BlobColumnType
import org.jetbrains.exposed.sql.IColumnType

class LongBlobColumnType : IColumnType by BlobColumnType() {
    override fun sqlType(): String = "LONGBLOB"
}

class MediumBlobColumnType : IColumnType by BlobColumnType() {
    override fun sqlType(): String = "MEDIUMBLOB"
}

class TinyBlobColumnType : IColumnType by BlobColumnType() {
    override fun sqlType(): String = "TINYBLOB"
}