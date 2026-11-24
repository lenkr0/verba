package io.github.lenkr0.verba.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Updating the app must keep every saved word. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VerbaDatabase::class.java)

    @Test
    fun version1To2KeepsWords() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                """
                INSERT INTO entries (id, language, target_language, lemma, lemma_key, translation, definition, created_at, updated_at)
                VALUES ('e1', 'en', 'de', 'linger', 'linger', 'verweilen', '', 1, 1)
                """,
            )
            execSQL(
                """
                INSERT INTO contexts (id, entry_id, sentence, word_form, word_start, word_end, created_at, updated_at)
                VALUES ('c1', 'e1', 'The smell lingered.', 'lingered', 10, 18, 1, 1)
                """,
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), VerbaDatabase::class.java, name).build()
        try {
            val list = runBlocking { VocabRepository(db).entryList().first() }
            assertEquals(listOf("linger"), list.map { it.entry.lemma })
            assertEquals(LearningStatus.New.code, list.single().learningStatus)
        } finally {
            db.close()
        }
    }
}
