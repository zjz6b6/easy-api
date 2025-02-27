package com.itangcent.idea.plugin.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.script.GroovyActionExtLoader
import com.itangcent.idea.plugin.script.LoggerBuffer
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.ConfigurableLogger
import com.itangcent.intellij.actions.ActionEventDataContextAdaptor
import com.itangcent.intellij.actions.KotlinAnAction
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.ConsoleRunnerLogger
import com.itangcent.intellij.logger.Logger
import javax.swing.Icon

abstract class BasicAnAction : KotlinAnAction {
    constructor() : super()
    constructor(icon: Icon?) : super(icon)
    constructor(text: String?) : super(text)
    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    protected open fun actionName(): String {
        return this::class.simpleName!!
    }

    override fun onBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {
        super.onBuildActionContext(event, builder)
        builder.bindInstance("plugin.name", "easy_api")

        builder.bind(SettingBinder::class) { it.toInstance(ServiceManager.getService(SettingBinder::class.java)) }
        builder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
        builder.bind(Logger::class, "delegate.logger") { it.with(ConsoleRunnerLogger::class).singleton() }

        afterBuildActionContext(event, builder)

        loadCustomActionExt(actionName(), ActionEventDataContextAdaptor(event), builder)
    }

    override fun actionPerformed(actionContext: ActionContext, project: Project?, anActionEvent: AnActionEvent) {
        val loggerBuffer: LoggerBuffer? = actionContext.getCache<LoggerBuffer>("LOGGER_BUF")
        loggerBuffer?.drainTo(actionContext.instance(Logger::class))
        val actionExtLoader: GroovyActionExtLoader? = actionContext.getCache<GroovyActionExtLoader>("GROOVY_ACTION_EXT_LOADER")
        actionExtLoader?.let { extLoader ->
            actionContext.on(EventKey.ONCOMPLETED) {
                extLoader.close()
            }
        }
    }

    protected open fun afterBuildActionContext(event: AnActionEvent, builder: ActionContext.ActionContextBuilder) {

    }

    protected fun loadCustomActionExt(actionName: String, event: DataContext,
                                      builder: ActionContext.ActionContextBuilder) {
        val logger = LoggerBuffer()
        builder.cache("LOGGER_BUF", logger)
        val actionExtLoader = GroovyActionExtLoader()
        builder.cache("GROOVY_ACTION_EXT_LOADER", actionExtLoader)
        val loadActionExt = actionExtLoader.loadActionExt(event, actionName, logger)
                ?: return
        loadActionExt.init(builder)
    }
}