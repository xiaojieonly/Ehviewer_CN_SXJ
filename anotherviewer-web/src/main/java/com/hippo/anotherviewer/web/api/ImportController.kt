package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.dto.JobSubmitResponse
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.service.EhImportService
import com.hippo.anotherviewer.web.service.Job
import com.hippo.anotherviewer.web.service.JobService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 原版 EhViewer 备份导入端点（B2 异步化，方案附录 A2）。鉴权与其余 /api 一致
 * （Bearer token + 管理员，见 SecurityConfig）。
 *
 * HTTP 线程只做同步准备（prep：空文件校验 + 落临时文件 + 读 cookies 字节——
 * MultipartFile 必须在 HTTP 线程读完），随后提交 IMPORT 任务并 202 返回
 * {jobId, state}；重活（[EhImportService.runImport]，事务线程绑定）在 JobService
 * 线程池运行，进度经 WS /topic/jobs/{jobId} 推送。失败返回统一错误 envelope
 * （M-6）：同 type 活跃任务 → 409 code=CONFLICT；其余（含空文件 prep 校验）→
 * 400 code=IMPORT_FAILED。
 */
@RestController
@RequestMapping("/api/v1/backup")
class ImportController(
    private val ehImportService: EhImportService,
    private val jobService: JobService,
) {

    private val logger = LoggerFactory.getLogger(ImportController::class.java)

    /** 上传 EhViewer 导出 .db（+ 可选 cookie db/JSON）→ 202 提交 IMPORT 任务（A2）。 */
    @PostMapping("/import-ehviewer")
    fun importEhViewer(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "cookies", required = false) cookies: MultipartFile?,
        @RequestParam(value = "force", defaultValue = "false") force: Boolean,
        authentication: Authentication,
    ): ResponseEntity<*> {
        return try {
            val username = authentication.name
            var prepared: EhImportService.PreparedImport? = null
            lateinit var job: Job
            job = jobService.submit(JobType.IMPORT, {
                prepared = ehImportService.prepareImport(file, cookies)
            }) { handle ->
                val p = prepared ?: throw IllegalStateException("导入任务准备未完成")
                // worker 自行写终态 result（JobService 不接管 worker 返回值）。
                job.result = ehImportService.runImport(p.dbPath, p.cookieBytes, force, username, handle)
            }
            ResponseEntity.status(HttpStatus.ACCEPTED).body(JobSubmitResponse(job.jobId, job.state))
        } catch (e: IllegalStateException) {
            logger.warn("EhViewer 导入任务提交被拒: {}", e.message)
            errorEnvelope(HttpStatus.CONFLICT, "CONFLICT", e.message ?: "已有导入任务进行中")
        } catch (e: Exception) {
            logger.warn("EhViewer 备份导入提交失败: {}", e.message)
            errorEnvelope(HttpStatus.BAD_REQUEST, "IMPORT_FAILED", e.message ?: "导入失败")
        }
    }
}
