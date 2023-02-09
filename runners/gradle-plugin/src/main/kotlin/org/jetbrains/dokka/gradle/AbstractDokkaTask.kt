package org.jetbrains.dokka.gradle

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.submit
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import org.jetbrains.dokka.*
import org.jetbrains.dokka.gradle.workers.DokkaGeneratorWorker
import org.jetbrains.dokka.plugability.ConfigurableBlock
import org.jetbrains.dokka.plugability.DokkaPlugin
import java.io.File
import javax.inject.Inject
import kotlin.reflect.full.createInstance

@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class AbstractDokkaTask : DefaultTask() {

    @Input
    val moduleName: Property<String> = project.objects.safeProperty<String>()
        .safeConvention(project.name)

    @Input
    val moduleVersion: Property<String> = project.objects.safeProperty<String>()
        .safeConvention(project.version.toString())

    @OutputDirectory
    val outputDirectory: Property<File> = project.objects.safeProperty<File>()
        .safeConvention(project.provider { defaultDokkaOutputDirectory() })

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val cacheRoot: Property<File?> = project.objects.safeProperty()

    @Input
    val failOnWarning: Property<Boolean> = project.objects.safeProperty<Boolean>()
        .safeConvention(DokkaDefaults.failOnWarning)

    @Input
    val suppressObviousFunctions: Property<Boolean> = project.objects.safeProperty<Boolean>()
        .safeConvention(DokkaDefaults.suppressObviousFunctions)

    @Input
    val suppressInheritedMembers: Property<Boolean> = project.objects.safeProperty<Boolean>()
        .safeConvention(DokkaDefaults.suppressInheritedMembers)

    @Input
    val offlineMode: Property<Boolean> = project.objects.safeProperty<Boolean>()
        .safeConvention(DokkaDefaults.offlineMode)

    @get:Input
    abstract val finalizeCoroutines: Property<Boolean>

    @Input
    val pluginsConfiguration: ListProperty<in DokkaConfiguration.PluginConfiguration> = project.objects.listProperty()

    /**
     * Used to keep compatibility with gradle using Kotlin lower than 1.3.50
     */
    @Input
    val pluginsMapConfiguration: MapProperty<String, String> = project.objects.mapProperty()

    inline fun <reified P : DokkaPlugin, reified T : ConfigurableBlock> pluginConfiguration(block: T.() -> Unit) {
        val instance = T::class.createInstance().apply(block)
        val pluginConfiguration = PluginConfigurationImpl(
            fqPluginName = P::class.qualifiedName!!,
            serializationFormat = DokkaConfiguration.SerializationFormat.JSON,
            values = instance.toJsonString()
        )
        pluginsConfiguration.add(pluginConfiguration)
    }

    @Classpath
    val plugins: Configuration = project.maybeCreateDokkaPluginConfiguration(name)

    @Classpath
    val runtime: Configuration = project.maybeCreateDokkaRuntimeConfiguration(name)

    @get:Inject
    protected abstract val workers: WorkerExecutor

    final override fun doFirst(action: Action<in Task>): Task = super.doFirst(action)

    final override fun doFirst(action: Closure<*>): Task = super.doFirst(action)

    @TaskAction
    internal open fun generateDocumentation() {
        val builtDokkaConfig = buildDokkaConfiguration()

        val workQueue = workers.processIsolation {
            println("Runtime configuration")
            runtime.resolve().toList().forEach { println(it) }
            classpath.setFrom(runtime, plugins)
            forkOptions {
                defaultCharacterEncoding = "UTF-8"
                maxHeapSize = "1g"
                //jvmArgs = listOf("-XX:MaxHeapSize=4g")
            }
        }

        workQueue.submit(DokkaGeneratorWorker::class) {
            dokkaConfiguration.set(builtDokkaConfig)
        }
    }

    internal abstract fun buildDokkaConfiguration(): DokkaConfigurationImpl

    init {
        group = JavaBasePlugin.DOCUMENTATION_GROUP
    }

    internal fun buildPluginsConfiguration(): List<PluginConfigurationImpl> {
        val manuallyConfigured = pluginsMapConfiguration.getSafe().entries.map { entry ->
            PluginConfigurationImpl(
                entry.key,
                DokkaConfiguration.SerializationFormat.JSON,
                entry.value
            )
        }
        return pluginsConfiguration.getSafe().mapNotNull { it as? PluginConfigurationImpl } + manuallyConfigured
    }
}
