package app.ironlog.personal.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.ironlog.personal.ui.components.IronCard
import app.ironlog.personal.ui.components.Page
import app.ironlog.personal.ui.components.SectionHeader
import app.ironlog.personal.ui.theme.IronlogTheme

/** Privacy explanation Health Connect links to from its permission screens. */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IronlogTheme(dark = true) { HealthPrivacy(onBack = ::finish) } }
    }
}

@Composable
fun HealthPrivacy(onBack: () -> Unit) {
    Page("Health data in Ironlog", onBack = onBack) {
        IronCard {
            SectionHeader("What Ironlog reads")
            Text("Daily step counts and sleep sessions from Health Connect. Ironlog does not read any other health data and never writes to Health Connect.")
        }
        IronCard {
            SectionHeader("How it is used")
            Text("Steps and hours slept fill your daily log, readiness score, streaks and achievements. Values you typed in yourself are kept: sleep you entered is never replaced, and steps you entered are only replaced by a higher measured count.")
        }
        IronCard {
            SectionHeader("Where it goes")
            Text("Nowhere. The data stays in Ironlog's storage on this phone. It is included only in backups you make yourself (a file you export, or your own Google Drive if you choose). Ironlog has no servers, ads or analytics.")
        }
        IronCard {
            SectionHeader("Stopping")
            Text("Turn off sync in Ironlog's Settings, or remove Ironlog's access in Health Connect at any time. Imported values already in your log stay until you delete them.")
        }
    }
}
