package com.itangcent.idea.plugin.api.export.suv

import com.itangcent.intellij.context.ActionContext
import kotlin.reflect.KClass

fun <T : Any> ActionContext.ActionContextBuilder.inheritFrom(parent: ActionContext, cls: KClass<T>) {
    this.bindInstance(cls, BeanWrapperProxies.wrap(cls, parent.instance(cls)))
}