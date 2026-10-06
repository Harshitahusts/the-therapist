package app.haven.companion.data

import app.haven.companion.BuildConfig

object Config {
    val backendUrl: String = BuildConfig.BACKEND_URL.trimEnd('/')
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    const val NETWORK_ERROR_MESSAGE =
        "I'm having trouble connecting right now. Please check your internet connection and try again."
}
