package com.minhquanghp86.frigatenvr.ui

/** Nhãn phiên bản hiển thị: chuẩn hoá "v0.13.0"/"0.13.0" -> "V0.13.0". */
val appVersionLabel: String
    get() {
        val raw = com.minhquanghp86.frigatenvr.BuildConfig.VERSION_NAME.trim()
        val core = raw.removePrefix("v").removePrefix("V")
        return "V$core"
    }
