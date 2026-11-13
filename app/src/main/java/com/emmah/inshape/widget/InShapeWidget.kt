package com.emmah.inshape.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.emmah.inshape.*
import java.time.LocalDate
import com.emmah.inshape.ui.theme.displayDate

class InShapeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(160.dp, 130.dp), DpSize(250.dp, 190.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as InShapeApplication).container
        val state = widgetState(container.repository.snapshot(), LocalDate.now())
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(androidx.glance.color.ColorProvider(Color(0xFFF3F4F6), Color(0xFF171B20))).padding(16.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("InShape", style = TextStyle(color = androidx.glance.color.ColorProvider(Color(0xFF12161B), Color(0xFFF2F4F5)), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text("Week of ${state.week.displayDate()}", style = TextStyle(color = androidx.glance.color.ColorProvider(Color(0xFF5A6470), Color(0xFFA3ADB8)), fontSize = 12.sp))
                Text(if (state.goal == null) "${state.count} workouts" else "${state.count} of ${state.goal} workouts", style = TextStyle(color = androidx.glance.color.ColorProvider(Color(0xFF12161B), Color(0xFFF2F4F5)), fontSize = 18.sp))
                if (LocalSize.current.height >= 190.dp && state.latest != null) Text("Last: ${state.latest}", style = TextStyle(color = androidx.glance.color.ColorProvider(Color(0xFF5A6470), Color(0xFFA3ADB8)), fontSize = 12.sp), maxLines = 3)
                Spacer(GlanceModifier.height(8.dp))
                Button(if (state.resume) "Resume workout" else "Start workout",
                    actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.START_OR_RESUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)),
                    colors = ButtonDefaults.buttonColors(backgroundColor = ColorProvider(Color(0xFFC5F25A)), contentColor = ColorProvider(Color(0xFF141A05))))
            }
        }
    }
}

class InShapeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = InShapeWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); WidgetUpdater.enqueue(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); WidgetUpdater.cancel(context) }
}
