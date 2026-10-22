package ai.venice.pubgcompanion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val RmRed = Color(0xFFFF2A3C)
val RmCyan = Color(0xFF00E5FF)
val Bg0 = Color(0xFF07070A)
val Bg1 = Color(0xFF101014)
val Bg2 = Color(0xFF18181E)
val Edge = Color(0xFF26262E)
val FgMain = Color(0xFFF2F3F5)
val FgMuted = Color(0xFF8A8F98)
val Good = Color(0xFF3DDC84)
val Bad = RmRed

private val Colors = darkColorScheme(
    primary = RmRed,
    onPrimary = Color.Black,
    secondary = RmCyan,
    onSecondary = Color.Black,
    background = Bg0,
    onBackground = FgMain,
    surface = Bg1,
    onSurface = FgMain,
    surfaceVariant = Bg2,
    onSurfaceVariant = FgMuted,
    outline = Edge,
    error = Bad,
)

private val RmShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = RmShapes, content = content)
}
