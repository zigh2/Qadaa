package com.qadaa.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val PrayerKey = ActionParameters.Key<Int>("prayer")

class QadaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Settings.init(context)
        val all = Db.get(context).dao().getAll()
        val t = today()
        val ar = Settings.arabic
        val types = Settings.activeTypes()
        val pend = all.filter { !it.done }.groupingBy { it.prayer }.eachCount()
        val doneT = all.filter { it.done && it.doneDay == t }.groupingBy { it.prayer }.eachCount()
        val rows = types.map { Triple(it.ordinal, pend[it.ordinal] ?: 0, doneT[it.ordinal] ?: 0) }
        val names = types.associate { it.ordinal to it.label(ar) }
        provideContent { WidgetContent(rows, names, ar) }
    }
}

@Composable
private fun WidgetContent(rows: List<Triple<Int, Int, Int>>, names: Map<Int, String>, ar: Boolean) {
    val white = ColorProvider(Color.White)
    val dim = ColorProvider(Color(0xFFB8D8D3))
    Column(
        GlanceModifier.fillMaxSize()
            .background(ColorProvider(Color(0xFF0E3B37)))
            .padding(12.dp)
            .cornerRadius(20.dp)
    ) {
        Text(
            if (ar) "قضاء الصلوات" else "Qadaa",
            style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        )
        Spacer(GlanceModifier.height(6.dp))
        rows.forEach { r ->
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(names[r.first] ?: "", style = TextStyle(color = white, fontSize = 14.sp))
                    Text(
                        (if (ar) "المتبقي " else "Left ") + r.second,
                        style = TextStyle(color = dim, fontSize = 11.sp)
                    )
                }
                Text(
                    r.third.toString(),
                    modifier = GlanceModifier.padding(horizontal = 10.dp),
                    style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
                Text(
                    "+",
                    modifier = GlanceModifier
                        .background(ColorProvider(Color(0xFF14B8A6)))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clickable(actionRunCallback<AddAction>(actionParametersOf(PrayerKey to r.first))),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF00201C)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}

class AddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val p = parameters[PrayerKey] ?: return
        Repo.addDone(context, p, today())
        QadaWidget().updateAll(context)
    }
}

class QadaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QadaWidget()
}
