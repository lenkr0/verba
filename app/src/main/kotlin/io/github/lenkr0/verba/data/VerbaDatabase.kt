package io.github.lenkr0.verba.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EntryEntity::class, ContextEntity::class, SourceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VerbaDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao

    companion object {
        fun create(context: Context): VerbaDatabase =
            Room.databaseBuilder(context, VerbaDatabase::class.java, "verba.db").build()

        fun inMemory(context: Context): VerbaDatabase =
            Room.inMemoryDatabaseBuilder(context, VerbaDatabase::class.java).build()
    }
}
