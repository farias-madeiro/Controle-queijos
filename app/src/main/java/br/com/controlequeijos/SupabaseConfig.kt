package br.com.controlequeijos

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue

val controleQueijosSupabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    install(GoTrue) {
        alwaysAutoRefresh = true
        autoLoadFromStorage = true
    }
}