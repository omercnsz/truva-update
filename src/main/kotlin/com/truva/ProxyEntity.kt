package com.truva

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,        // Örn: "İsveç - Stockholm"
    val region: String,      // Örn: "SE", "FI", "LT"
    val ip: String,
    val port: Int = 443,
    val uuid: String,
    val publicKey: String,
    val shortId: String,
    val sni: String = "google.com",
    val password: String = "",
    val flow: String = "xtls-rprx-vision",   // bazı sunucularda boş olabilir
    val security: String = "reality",         // reality / tls / none
    val network: String = "tcp",              // tcp / ws / grpc / h2
    val fingerprint: String = "chrome",       // uTLS fingerprint
    val path: String = "/",                      // ws path veya grpc serviceName
    val isSelected: Boolean = false,
    val latency: Long? = null
)
