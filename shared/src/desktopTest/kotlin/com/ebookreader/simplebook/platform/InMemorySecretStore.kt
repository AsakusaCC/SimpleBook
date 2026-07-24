package com.ebookreader.simplebook.platform

/** 纯进程内 SecretStore，desktopTest 专用，绝不触碰真 Keychain。 */
class InMemorySecretStore : SecretStore {
    private val map = mutableMapOf<String, String>()
    override fun read(account: String): String? = map[account]
    override fun write(account: String, value: String) { map[account] = value }
    override fun delete(account: String) { map.remove(account) }
}
