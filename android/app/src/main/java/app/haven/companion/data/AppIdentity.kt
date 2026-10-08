package app.haven.companion.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

/**
 * Identifies this app to Google on every Gemini request (X-Android-Package / X-Android-Cert), so the
 * API key can be restricted in Google Cloud to *this* app signed with *this* certificate. A key copied
 * out of the APK is then useless anywhere else.
 */
class AppIdentityInterceptor(context: Context) : Interceptor {
    private val packageName: String = context.packageName
    private val certSha1: String? = runCatching { signingCertSha1(context) }.getOrNull()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith("googleapis.com")) return chain.proceed(request)
        val b = request.newBuilder().header("X-Android-Package", packageName)
        certSha1?.let { b.header("X-Android-Cert", it) }
        return chain.proceed(b.build())
    }

    private companion object {
        @Suppress("DEPRECATION")
        fun signingCertSha1(context: Context): String? {
            val pm = context.packageManager
            val cert: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                val sigs = if (info?.hasMultipleSigners() == true) info.apkContentsSigners else info?.signingCertificateHistory
                sigs?.firstOrNull()?.toByteArray()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
            }
            return cert?.let { bytes ->
                MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
            }
        }
    }
}
