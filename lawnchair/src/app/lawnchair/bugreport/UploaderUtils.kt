package app.lawnchair.bugreport

object UploaderUtils {

    private val katbinService = KatbinService.create()
    // Glow: crash reports are never sent to a third-party paste site; copy or share them instead
    const val IS_ALIVE_AVAILABLE = false

    suspend fun upload(report: BugReport): String {
        val body = KatbinUploadBody(KatbinPaste(content = report.contents))
        val result = katbinService.upload(body)
        return "https://katb.in/${result.id}"
    }
}
