package fr.conichonhaa.nakivo.data

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object CertUtils {
    /** Empreinte SHA-256 du certificat, en hexadécimal majuscule séparé par « : ». */
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }
}

/**
 * Trust manager « confiance à la première utilisation » :
 *  - accepte un certificat valide selon les autorités système ;
 *  - accepte un certificat auto-signé uniquement si son empreinte SHA-256
 *    correspond à celle que l'utilisateur a validée ;
 *  - sinon refuse, en gardant le certificat vu pour le proposer à l'utilisateur.
 */
class PinningTrustManager(private val pinnedSha256: String?) : X509TrustManager {

    @Volatile
    var lastSeen: X509Certificate? = null
        private set

    private val system: X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        system.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        if (chain.isEmpty()) throw CertificateException("Chaîne de certificats vide")
        val leaf = chain[0]
        lastSeen = leaf
        if (pinnedSha256 != null && CertUtils.sha256(leaf) == pinnedSha256) return
        system.checkServerTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}
