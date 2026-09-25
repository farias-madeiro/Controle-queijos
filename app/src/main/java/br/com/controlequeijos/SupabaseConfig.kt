package br.com.controlequeijos

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth

val controleQueijosSupabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    install(Auth) {
        alwaysAutoRefresh = true
        autoLoadFromStorage = true
    }
}