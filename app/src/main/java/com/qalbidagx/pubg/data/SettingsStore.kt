package com.qalbidagx.pubg.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("qalbidagx_settings")

class SettingsStore(private val ctx: Context) {

    private val ds = ctx.dataStore

    val gameModeEnabled: Flow<Boolean> = ds.data.map { it[GAME_MODE] ?: false }
    val overlayEnabled: Flow<Boolean> = ds.data.map { it[OVERLAY] ?: true }
    val overlayTouchable: Flow<Boolean> = ds.data.map { it[TOUCHABLE] ?: true }
    val pingHost: Flow<String> = ds.data.map { it[HOST] ?: "1.1.1.1" }
    val baseDpi: Flow<Int> = ds.data.map { it[BASE_DPI] ?: 400 }
    val sensitivityCode: Flow<String> = ds.data.map { it[SENS] ?: "" }
    val accentColor: Flow<Int> = ds.data.map { it[ACCENT] ?: 0xFFFF2A3C.toInt() }

    val crosshairEnabled: Flow<Boolean> = ds.data.map { it[X_ON] ?: false }
    val crosshairTouchable: Flow<Boolean> = ds.data.map { it[X_TOUCH] ?: true }
    val crosshairX: Flow<Int> = ds.data.map { it[X_X] ?: 0 }
    val crosshairY: Flow<Int> = ds.data.map { it[X_Y] ?: 0 }
    val crosshairShape: Flow<String> = ds.data.map { it[X_SHAPE] ?: "crossdot" }
    val crosshairColor: Flow<Int> = ds.data.map { it[X_COLOR] ?: 0xFFFF2A3C.toInt() }
    val crosshairSize: Flow<Int> = ds.data.map { it[X_SIZE] ?: 22 }
    val crosshairThickness: Flow<Int> = ds.data.map { it[X_THICK] ?: 3 }
    val crosshairGap: Flow<Int> = ds.data.map { it[X_GAP] ?: 8 }
    val crosshairDot: Flow<Int> = ds.data.map { it[X_DOT] ?: 3 }
    val crosshairAlpha: Flow<Int> = ds.data.map { it[X_ALPHA] ?: 220 }
    val crosshairOutline: Flow<Boolean> = ds.data.map { it[X_OUTLINE] ?: true }

    suspend fun setGameMode(v: Boolean) = ds.edit { it[GAME_MODE] = v }
    suspend fun setOverlay(v: Boolean) = ds.edit { it[OVERLAY] = v }
    suspend fun setTouchable(v: Boolean) = ds.edit { it[TOUCHABLE] = v }
    suspend fun setPingHost(v: String) = ds.edit { it[HOST] = v }
    suspend fun setBaseDpi(v: Int) = ds.edit { it[BASE_DPI] = v }
    suspend fun setSensitivityCode(v: String) = ds.edit { it[SENS] = v }
    suspend fun setAccent(v: Int) = ds.edit { it[ACCENT] = v }

    suspend fun setCrosshairEnabled(v: Boolean) = ds.edit { it[X_ON] = v }
    suspend fun setCrosshairTouchable(v: Boolean) = ds.edit { it[X_TOUCH] = v }
    suspend fun setCrosshairPos(x: Int, y: Int) = ds.edit { it[X_X] = x; it[X_Y] = y }
    suspend fun setCrosshairShape(v: String) = ds.edit { it[X_SHAPE] = v }
    suspend fun setCrosshairColor(v: Int) = ds.edit { it[X_COLOR] = v }
    suspend fun setCrosshairSize(v: Int) = ds.edit { it[X_SIZE] = v }
    suspend fun setCrosshairThickness(v: Int) = ds.edit { it[X_THICK] = v }
    suspend fun setCrosshairGap(v: Int) = ds.edit { it[X_GAP] = v }
    suspend fun setCrosshairDot(v: Int) = ds.edit { it[X_DOT] = v }
    suspend fun setCrosshairAlpha(v: Int) = ds.edit { it[X_ALPHA] = v }
    suspend fun setCrosshairOutline(v: Boolean) = ds.edit { it[X_OUTLINE] = v }

    private companion object {
        val GAME_MODE = booleanPreferencesKey("game_mode")
        val OVERLAY = booleanPreferencesKey("overlay")
        val TOUCHABLE = booleanPreferencesKey("overlay_touchable")
        val HOST = stringPreferencesKey("ping_host")
        val BASE_DPI = intPreferencesKey("base_dpi")
        val SENS = stringPreferencesKey("sensitivity")
        val ACCENT = intPreferencesKey("accent")

        val X_ON = booleanPreferencesKey("x_on")
        val X_TOUCH = booleanPreferencesKey("x_touch")
        val X_X = intPreferencesKey("x_x")
        val X_Y = intPreferencesKey("x_y")
        val X_SHAPE = stringPreferencesKey("x_shape")
        val X_COLOR = intPreferencesKey("x_color")
        val X_SIZE = intPreferencesKey("x_size")
        val X_THICK = intPreferencesKey("x_thick")
        val X_GAP = intPreferencesKey("x_gap")
        val X_DOT = intPreferencesKey("x_dot")
        val X_ALPHA = intPreferencesKey("x_alpha")
        val X_OUTLINE = booleanPreferencesKey("x_outline")
    }
}
