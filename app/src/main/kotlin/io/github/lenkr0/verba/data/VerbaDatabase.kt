package io.github.lenkr0.verba.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EntryEntity::class, ContextEntity::class, SourceEntity::class,
        ReviewCardEntity::class, ReviewLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // A schema change must keep the user's words: each version gets a migration (see app/schemas/).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class VerbaDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao

    abstract fun reviewDao(): ReviewDao

    companion object {
        fun create(context: Context): VerbaDatabase =
            Room.databaseBuilder(context, VerbaDatabase::class.java, "verba.db").build()

        fun inMemory(context: Context): VerbaDatabase =
            Room.inMemoryDatabaseBuilder(context, VerbaDatabase::class.java).build()
    }
}
