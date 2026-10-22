package ai.venice.pubgcompanion.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("companion_settings")

class SettingsStore(private val ctx: Context) {

    private val ds = ctx.dataStore

    val gameModeEnabled: Flow<Boolean> = ds.data.map { it[GAME_MODE] ?: false }
    val overlayEnabled: Flow<Boolean> = ds.data.map { it[OVERLAY] ?: true }
    val overlayTouchable: Flow<Boolean> = ds.data.map { it[TOUCHABLE] ?: true }
    val showCrosshair: Flow<Boolean> = ds.data.map { it[CROSSHAIR] ?: false }
    val crosshairSize: Flow<Int> = ds.data.map { it[CROSSHAIR_SIZE] ?: 18 }
    val pingHost: Flow<String> = ds.data.map { it[HOST] ?: "1.1.1.1" }
    val baseDpi: Flow<Int> = ds.data.map { it[BASE_DPI] ?: 400 }
    val sensitivityCode: Flow<String> = ds.data.map { it[SENS] ?: "" }

    suspend fun setGameMode(on: Boolean) = ds.edit { it[GAME_MODE] = on }
    suspend fun setOverlay(on: Boolean) = ds.edit { it[OVERLAY] = on }
    suspend fun setTouchable(on: Boolean) = ds.edit { it[TOUCHABLE] = on }
    suspend fun setCrosshair(on: Boolean) = ds.edit { it[CROSSHAIR] = on }
    suspend fun setCrosshairSize(px: Int) = ds.edit { it[CROSSHAIR_SIZE] = px }
    suspend fun setPingHost(h: String) = ds.edit { it[HOST] = h }
    suspend fun setBaseDpi(d: Int) = ds.edit { it[BASE_DPI] = d }
    suspend fun setSensitivityCode(c: String) = ds.edit { it[SENS] = c }

    private companion object {
        val GAME_MODE = booleanPreferencesKey("game_mode")
        val OVERLAY = booleanPreferencesKey("overlay")
        val TOUCHABLE = booleanPreferencesKey("overlay_touchable")
        val CROSSHAIR = booleanPreferencesKey("crosshair")
        val CROSSHAIR_SIZE = intPreferencesKey("crosshair_size")
        val HOST = stringPreferencesKey("ping_host")
        val BASE_DPI = intPreferencesKey("base_dpi")
        val SENS = stringPreferencesKey("sensitivity")
    }
}
