package com.ebookreader.simplebook.platform

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 抽象的 secrets 存储，便于在 Keychain（生产）与内存（测试）间切换。
 * TokenStorage 是唯一调用方。
 */
interface SecretStore {
    /** 读取指定 account 的值；不存在返回 null。 */
    fun read(account: String): String?
    /** 写入（存在则覆盖）。 */
    fun write(account: String, value: String)
    /** 删除；不存在不报错。 */
    fun delete(account: String)
}

/**
 * macOS Keychain 后端，spawn `/usr/bin/security` 读写 generic-password。
 * 所有 token 走同一 service 下、account="tokens" 的单一条目。
 *
 * 已知取舍：`-w <value>` 经 argv 传入，短暂可见于 `ps`（子秒级）。`security` CLI
 * 不支持 stdin 读 `-w`。缓解：owner-only 机器、OAuth token 可吊销、刷新每小时一次。
 */
internal class SecurityCliStore(
    private val service: String = "com.ebookreader.simplebook"
) : SecretStore {

    companion object {
        private const val EXIT_ITEM_NOT_FOUND = 44   // security CLI errSecItemNotFound
    }

    override fun read(account: String): String? {
        val out = runSecurity(listOf("find-generic-password", "-s", service, "-a", account, "-w"))
            ?: return null   // 条目不存在（退出码 44）或失败
        return out.trimEnd().let { pwd ->
            // 现代版本 `-w` 直接输出密码；老版本可能带 "password:" 前缀，兼容处理
            if (pwd.startsWith("password:")) pwd.removePrefix("password:").trim() else pwd
        }
    }

    override fun write(account: String, value: String) {
        val ok = runSecurity(
            listOf("add-generic-password", "-U", "-s", service, "-a", account, "-w", value)
        ) != null
        // 写入失败：token 仅在内存、未落盘 → 下次启动「莫名掉线」，必须留痕
        if (!ok) logW("SecurityCliStore", "write failed for account=$account")
    }

    override fun delete(account: String) {
        // 不存在（退出码 44）静默忽略
        runSecurity(listOf("delete-generic-password", "-s", service, "-a", account))
    }

    /**
     * 返回 stdout（成功），null 表示失败（非零退出码 / 超时 / IO 或中断异常）。
     *
     * 设计为「失败即降级为空」——任何异常都不应穿透到 TokenStorage/AuthProvider。
     * 失败时只记 args + 退出码/异常类型，**不记 out**（find/add -w 的输出含 token 明文）。
     * args 中 `-w` 后紧跟的 value（Base64 token）同样脱敏，避免 stdout/stderr 持久化
     * 日志泄漏 token 明文（比「ps 子秒可见」更严重）。
     */
    private fun runSecurity(args: List<String>): String? {
        val maskedArgs = maskArgs(args)
        val proc: Process = try {
            ProcessBuilder(listOf("/usr/bin/security") + args)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            logW("SecurityCliStore", "security $maskedArgs failed: ${e.javaClass.simpleName}")
            return null
        }
        try {
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val exited = proc.waitFor(10, TimeUnit.SECONDS) // 超时返回 false → 视为失败
            if (!exited) {
                logW("SecurityCliStore", "security $maskedArgs failed: timed out")
                return null
            }
            val code = proc.exitValue()
            if (code != 0) {
                if (code != EXIT_ITEM_NOT_FOUND) {   // 44=errSecItemNotFound：read/delete 无条目是预期，不告警
                    logW("SecurityCliStore", "security $maskedArgs failed: exit=$code")
                }
                return null
            }
            return out
        } catch (e: IOException) {
            logW("SecurityCliStore", "security $maskedArgs failed: ${e.javaClass.simpleName}")
            return null
        } catch (e: InterruptedException) {
            logW("SecurityCliStore", "security $maskedArgs failed: ${e.javaClass.simpleName}")
            return null
        } finally {
            // 保证进程回收（含超时/异常路径），避免 zombie security 子进程
            proc.destroy()
        }
    }

    /**
     * 把 `-w` 后紧跟的值替换为 "<redacted>"，其余元素保留。
     * 仅用于日志脱敏：read（`find-generic-password ... -w`）和 delete 的 `-w` 无参，不受影响。
     */
    private fun maskArgs(args: List<String>): List<String> {
        val out = args.toMutableList()
        for (i in out.indices) {
            if (out[i] == "-w" && i + 1 < out.size) {
                out[i + 1] = "<redacted>"
            }
        }
        return out
    }
}
