package com.iagodf.argos.setup

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * the adb client that talks to the phone's own wireless debugging. its key only lives while the setup runs:
 * [forget] deletes it, and uninstalling Argos Setup removes it anyway
 */
class Adb private constructor(private val key: PrivateKey, private val cert: Certificate) : AbsAdbConnectionManager() {
    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
    }

    override fun getPrivateKey() = key
    override fun getCertificate() = cert
    override fun getDeviceName() = "Argos Setup"

    /** runs one shell command and gives back what it printed */
    fun run(cmd: String): String {
        val s = openStream("shell:$cmd")
        val out = s.openInputStream().bufferedReader().use { it.readText() }
        runCatching { s.close() }
        return out.trim()
    }

    companion object {
        @Volatile private var one: Adb? = null

        private fun keyFile(ctx: Context) = File(ctx.filesDir, "adb.key")
        private fun certFile(ctx: Context) = File(ctx.filesDir, "adb.crt")

        fun get(ctx: Context): Adb = one ?: synchronized(this) { one ?: load(ctx).also { one = it } }

        fun forget(ctx: Context) {
            runCatching { one?.close() }
            one = null
            keyFile(ctx).delete(); certFile(ctx).delete()
        }

        private fun load(ctx: Context): Adb {
            val kf = keyFile(ctx); val cf = certFile(ctx)
            if (kf.exists() && cf.exists()) runCatching {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                val cert = CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream())
                return Adb(key, cert)
            }
            val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }
            val pair = gen.generateKeyPair()
            val alg = "SHA512withRSA"
            val name = X500Name("CN=Argos Setup")
            val from = Date()
            val to = Date(System.currentTimeMillis() + 7L * 86_400_000)
            val ext = CertificateExtensions()
            ext.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))
            ext.set("PrivateKeyUsage", PrivateKeyUsageExtension(from, to))
            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(alg)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(pair.public))
            info.set("validity", CertificateValidity(from, to))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", ext)
            val cert = X509CertImpl(info)
            cert.sign(pair.private, alg)
            kf.writeBytes(pair.private.encoded)
            cf.writeBytes(cert.encoded)
            return Adb(pair.private, cert)
        }
    }
}
