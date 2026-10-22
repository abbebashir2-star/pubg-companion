package ai.venice.pubgcompanion.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val enabled: Boolean = true,
    val perfMode: String = "balanced",
    val dnd: Boolean = true,
    val brightnessPercent: Int = 85,
    val refreshRateHz: Int = 120,
    val lowerResolution: Boolean = false,
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val resolutionDpi: Int = 320,
    val killBackground: Boolean = false,
    val thermalGuard: Boolean = true,
    val temperatureLimitC: Int = 42,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE enabled = 1")
    suspend fun enabledProfiles(): List<Profile>

    @Insert suspend fun insert(p: Profile): Long
    @Update suspend fun update(p: Profile)
    @Delete suspend fun delete(p: Profile)
}

@Database(entities = [Profile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "companion.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}

class ProfileRepository(private val dao: ProfileDao) {

    val all: Flow<List<Profile>> = dao.observeAll()

    suspend fun enabled(): List<Profile> = dao.enabledProfiles()

    suspend fun save(p: Profile) {
        if (p.id == 0L) dao.insert(p) else dao.update(p)
    }

    suspend fun delete(p: Profile) = dao.delete(p)

    suspend fun ensureDefault() {
        if (dao.enabledProfiles().isNotEmpty()) return
        dao.insert(Profile(name = "Daily", perfMode = "balanced", refreshRateHz = 120))
        dao.insert(
            Profile(
                name = "Ranked",
                perfMode = "rise",
                refreshRateHz = 120,
                lowerResolution = true,
                killBackground = true,
            )
        )
    }
}
