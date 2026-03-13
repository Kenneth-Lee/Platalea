package com.kenny.localmanager.gpg

/**
 * 进程内缓存的私钥密码，仅内存持有，不落盘、不导出。
 * 用于「启动解密密钥」开启后，启动时解锁一次，后续使用私钥不再询问。
 */
object SecretKeyPasswordCache {
    @Volatile
    private var cached: CharArray? = null

    fun get(): CharArray? = cached?.let { it.copyOf() }

    fun set(password: CharArray) {
        clear()
        cached = password.copyOf()
    }

    fun clear() {
        cached?.fill(' ')
        cached = null
    }
}
