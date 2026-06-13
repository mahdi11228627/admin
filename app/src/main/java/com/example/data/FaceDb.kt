package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "face_transformations")
data class FaceTransformation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val effectId: String,
    val effectName: String, // e.g., "پیر", "انیمه"
    val originalPath: String, // Absolute path to the locally saved original file
    val transformedPath: String, // Absolute path to the locally saved transformed file
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FaceTransformationDao {
    @Query("SELECT * FROM face_transformations ORDER BY timestamp DESC")
    fun getAllTransformations(): Flow<List<FaceTransformation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transformation: FaceTransformation)

    @Query("DELETE FROM face_transformations WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM face_transformations")
    suspend fun deleteAll()
}

@Database(entities = [FaceTransformation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun faceTransformationDao(): FaceTransformationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_face_shifter_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class FaceRepository(private val dao: FaceTransformationDao) {
    val allTransformations: Flow<List<FaceTransformation>> = dao.getAllTransformations()

    suspend fun insert(transformation: FaceTransformation) {
        dao.insert(transformation)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
