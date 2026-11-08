package com.davicolzer.zureader

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Idioma do app: detectar pelo celular (padrão), português do Brasil ou inglês. Sem tradução para
 * o idioma do celular, o Android usa o inglês (`values/`); o português fica em `values-pt/`.
 *
 * No Android 13+ a escolha é do sistema (idioma por app, aparece também nas configurações do
 * Android). Antes disso, o app guarda a escolha e aplica no contexto da tela ([wrap]).
 */
enum class AppLanguage(val tag: String?) {
    AUTO(null),
    PORTUGUESE("pt-BR"),
    ENGLISH("en"),
    ;

    companion object {
        private const val PREFS = "language"
        private const val KEY = "tag"

        fun of(tag: String?): AppLanguage = when {
            tag.isNullOrEmpty() -> AUTO
            tag.startsWith("pt") -> PORTUGUESE
            tag.startsWith("en") -> ENGLISH
            else -> AUTO
        }

        fun current(context: Context): AppLanguage =
            if (Build.VERSION.SDK_INT >= 33) {
                val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
                of(if (locales.isEmpty) null else locales[0].toLanguageTag())
            } else {
                of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
            }

        /** Troca o idioma; a tela é recriada no novo idioma. */
        fun set(activity: Activity, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= 33) {
                // o sistema guarda e recria a tela sozinho
                activity.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(language.tag.orEmpty())
            } else {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
                activity.recreate()
            }
        }

        /** Contexto da tela no idioma escolhido (só antes do Android 13; depois, o sistema cuida). */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= 33) return base
            val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            return base.createConfigurationContext(config)
        }
    }
}
