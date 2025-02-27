package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiAnnotationUtils
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.traceError
import org.apache.commons.lang3.StringUtils
import kotlin.reflect.KClass

/**
 * only parse name
 */
open class SimpleRequestClassExporter : ClassExporter, Worker {

    override fun support(docType: KClass<*>): Boolean {
        return docType == Request::class
    }

    private var statusRecorder: StatusRecorder = StatusRecorder()

    override fun status(): WorkerStatus {
        return statusRecorder.status()
    }

    override fun waitCompleted() {
        return statusRecorder.waitCompleted()
    }

    override fun cancel() {
        return statusRecorder.cancel()
    }

    @Inject
    private val logger: Logger? = null

    @Inject
    private val docParseHelper: DocParseHelper? = null

    @Inject
    protected val requestHelper: RequestHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private var actionContext: ActionContext? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (cls !is PsiClass) return false
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !isCtrl(cls) -> return false
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return true
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    foreachMethod(cls) { method ->
                        exportMethodApi(method, docHandle)
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
        return true
    }

    private fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringClassName.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        }
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(method: PsiMethod, docHandle: DocHandle) {

        actionContext!!.checkStatus()
        //todo:support other web annotation
        findRequestMappingInAnn(method) ?: return
        val request = Request()
        request.resource = method

        val attr: String?
        val attrOfMethod = findAttrOfMethod(method)!!
        val lines = attrOfMethod.lines()
        attr = if (lines.size > 1) {//multi line
            lines.firstOrNull { it.isNotBlank() }
        } else {
            attrOfMethod
        }

        requestHelper!!.setName(request, attr ?: method.name)
        docHandle(request)
    }

    private fun findRequestMappingInAnn(ele: PsiModifierListOwner): PsiAnnotation? {
        return SPRING_REQUEST_MAPPING_ANNOTATIONS
                .map { PsiAnnotationUtils.findAnn(ele, it) }
                .firstOrNull { it != null }
    }

    private fun findAttrOfMethod(method: PsiMethod): String? {
        val docComment = method.docComment

        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> method.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, method)
        }
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    companion object {
        val SPRING_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(SpringClassName.REQUESTMAPPING_ANNOTATION,
                SpringClassName.GET_MAPPING,
                SpringClassName.DELETE_MAPPING,
                SpringClassName.PATCH_MAPPING,
                SpringClassName.POST_MAPPING,
                SpringClassName.PUT_MAPPING)
    }
}