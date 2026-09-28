package com.github.jaideepaher.slatebrowser.ssl

import java.util.Date

data class SslCertificateInfo(
    val issuedByCommonName: String,
    val issuedToCommonName: String,
    val issuedToOrganizationName: String?,
    val issueDate: Date,
    val expireDate: Date,
    val sslState: SslState
)
