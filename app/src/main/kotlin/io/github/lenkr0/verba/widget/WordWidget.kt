package io.github.lenkr0.verba.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import io.github.lenkr0.verba.MainActivity
import io.github.lenkr0.verba.R
import io.github.lenkr0.verba.appContainer
import io.github.lenkr0.verba.data.EntryListItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Word of the day" on the home screen. Nothing runs in the background: the
 * widget is drawn when it is placed, when Verba goes to the background (new
 * words) and at midnight by an inexact system alarm, which it sets again each
 * time.
 */
class WordWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_ENABLED, ACTION_DAILY,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
            -> {
                // Reading the database takes a moment: keep the broadcast alive until done
                val pending = goAsync()
                context.appContainer.scope.launch {
                    try {
                        refresh(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(dailyIntent(context))
    }

    companion object {
        private const val ACTION_DAILY = "io.github.lenkr0.verba.action.WORD_OF_THE_DAY"

        /** Redraws all placed widgets with today's word; does nothing when none is placed. */
        suspend fun refresh(context: Context, today: LocalDate = LocalDate.now()) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            if (ids.isEmpty()) return
            val container = context.appContainer
            val items = container.vocabulary.entryList().first()
            val id = WordOfTheDay.pick(items.map { it.entry.id }, today)
            manager.updateAppWidget(ids, views(context, items.firstOrNull { it.entry.id == id }, container.settings.settings.value.eInkMode))
            scheduleNextDay(context, today)
        }

        fun views(context: Context, item: EntryListItem?, eInk: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_word)
            if (item == null) {
                views.setTextViewText(R.id.widget_word, context.getString(R.string.widget_empty_title))
                views.setViewVisibility(R.id.widget_translation, View.GONE)
                views.setTextViewText(R.id.widget_sentence, context.getString(R.string.widget_empty_body))
                views.setViewVisibility(R.id.widget_sentence, View.VISIBLE)
            } else {
                val entry = item.entry
                views.setTextViewText(R.id.widget_word, entry.lemma)
                views.setTextViewText(R.id.widget_translation, entry.translation.ifBlank { entry.definition })
                views.setViewVisibility(R.id.widget_translation, if (entry.translation.isBlank() && entry.definition.isBlank()) View.GONE else View.VISIBLE)
                val sentence = item.latestSentence
                if (sentence.isNullOrBlank()) {
                    views.setViewVisibility(R.id.widget_sentence, View.GONE)
                } else {
                    views.setTextViewText(R.id.widget_sentence, highlighted(sentence, item.latestWordStart ?: -1, item.latestWordEnd ?: -1))
                    views.setViewVisibility(R.id.widget_sentence, View.VISIBLE)
                }
            }
            if (eInk) {
                // Ink on paper, no tones (the app's E-ink mode)
                views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_eink)
                for (text in listOf(R.id.widget_label, R.id.widget_word, R.id.widget_translation, R.id.widget_sentence)) {
                    views.setTextColor(text, Color.BLACK)
                }
            }
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, item?.entry?.id))
            return views
        }

        private fun highlighted(sentence: String, start: Int, end: Int): CharSequence {
            if (start !in 0..<end || end > sentence.length) return sentence
            return SpannableString(sentence).apply {
                setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        /** Opens the word's page, or the word list when there is no word. */
        private fun openIntent(context: Context, entryId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (entryId != null) intent.putExtra(MainActivity.EXTRA_ENTRY, entryId)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun dailyIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WordWidget::class.java).setAction(ACTION_DAILY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Shortly after the next midnight; inexact, so Android may bundle it with other alarms. */
        private fun scheduleNextDay(context: Context, today: LocalDate) {
            val next = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, dailyIntent(context))
        }
    }
}
