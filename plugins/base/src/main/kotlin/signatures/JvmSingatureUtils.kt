package org.jetbrains.dokka.base.signatures

import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.properties.WithExtraProperties

interface JvmSingatureUtils {

    fun PageContentBuilder.DocumentableContentBuilder.annotationsBlock(d: Documentable)

    fun PageContentBuilder.DocumentableContentBuilder.annotationsInline(d: Documentable)

    fun <T : Documentable> WithExtraProperties<T>.modifiers(): Set<ExtraModifiers>

    fun Set<ExtraModifiers>.toSignatureString(): String =
        joinToString("") { it.name.toLowerCase() + " " }

    fun <T : Documentable> WithExtraProperties<T>.annotations(): List<Annotations.Annotation> =
        extra[Annotations]?.content ?: emptyList()

    private fun Annotations.Annotation.toSignatureString(): String =
        "@${this.dri.classNames}(${this.params.entries.joinToString { it.key + "=" + it.value }})"

    private fun PageContentBuilder.DocumentableContentBuilder.annotations(
        d: Documentable,
        ignored: Set<Annotations.Annotation>,
        operation: (Annotations.Annotation) -> Unit
    ): Unit = when (d) {
        is DFunction -> d.annotations()
        is DProperty -> d.annotations()
        is DClass -> d.annotations()
        is DInterface -> d.annotations()
        is DObject -> d.annotations()
        is DEnum -> d.annotations()
        is DAnnotation -> d.annotations()
        is DTypeParameter -> d.annotations()
        is DEnumEntry -> d.annotations()
        is DTypeAlias -> d.annotations()
        is DParameter -> d.annotations()
        else -> null
    }?.let {
        it.filter { it !in ignored }.forEach {
            operation(it)
        }
    } ?: Unit

    fun PageContentBuilder.DocumentableContentBuilder.annotationsBlockWithIgnored(
        d: Documentable,
        ignored: Set<Annotations.Annotation>
    ) {
        annotations(d, ignored) {
            group {
                link(it.toSignatureString(), it.dri)
            }
        }
    }

    fun PageContentBuilder.DocumentableContentBuilder.annotationsInlineWithIgnored(
        d: Documentable,
        ignored: Set<Annotations.Annotation>
    ) {
        annotations(d, ignored) {
            link(it.toSignatureString(), it.dri)
            text(Typography.nbsp.toString())
        }
    }

    fun <T : Documentable> WithExtraProperties<T>.modifiersWithFilter(
        filter: Set<ExtraModifiers> = ExtraModifiers.values().toSet()
    ): Set<ExtraModifiers> =
        extra[AdditionalModifiers]?.content?.filter { it in filter }?.toSet() ?: emptySet()
}