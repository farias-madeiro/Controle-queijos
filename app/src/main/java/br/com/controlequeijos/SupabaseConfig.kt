package br.com.controlequeijos

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

val controleQueijosSupabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    install(Auth) {
        alwaysAutoRefresh = true
        autoLoadFromStorage = true
    }
    install(Postgrest)
}
