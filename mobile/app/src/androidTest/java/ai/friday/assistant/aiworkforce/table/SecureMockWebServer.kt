package ai.friday.assistant.aiworkforce.table

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

internal data class SecureMockWebServer(
    val server: MockWebServer,
    val client: OkHttpClient
)

internal fun secureMockWebServer(): SecureMockWebServer {
    val certificate = HeldCertificate.Builder()
        .addSubjectAlternativeName("localhost")
        .build()
    val serverCertificates = HandshakeCertificates.Builder()
        .heldCertificate(certificate)
        .build()
    val clientCertificates = HandshakeCertificates.Builder()
        .addTrustedCertificate(certificate.certificate)
        .build()
    val server = MockWebServer()
    server.useHttps(serverCertificates.sslSocketFactory(), false)
    server.start()
    val client = OkHttpClient.Builder()
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .build()
    return SecureMockWebServer(server, client)
}
