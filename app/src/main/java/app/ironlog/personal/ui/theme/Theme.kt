package app.ironlog.personal.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark=darkColorScheme(primary=Color(0xFFD9FF4F),onPrimary=Color(0xFF1B1D17),background=Color(0xFF10120E),surface=Color(0xFF191B17),onSurface=Color(0xFFF2F2EA),secondary=Color(0xFFB8C69B))
private val Light=lightColorScheme(primary=Color(0xFF465E00),background=Color(0xFFF6F7F0),surface=Color(0xFFFFFFFF))
@Composable fun IronlogTheme(dark:Boolean=true,content:@Composable ()->Unit) { MaterialTheme(colorScheme=if(dark) Dark else Light,content=content) }
