package com.bmesta.mvntoolkit.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.idea.maven.dom.DependencyConflictId
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.generate.GenerateManagedDependencyAction
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.*

/**
 * @author Baptiste Mesta
 */
class ModulesMerger(val project: Project, val from: MavenProject, val into: MavenProject) {


    private val LOGGER = Logger.getInstance("#com.bmesta.mvntoolkit.action.ModulesMerger")

    fun merge(progressIndicator: ProgressIndicator) {

        doMerge(progressIndicator)
    }

    fun doMerge(progressIndicator: ProgressIndicator) {

        progressIndicator.fraction = 0.10

//        MavenDomProjectProcessorUtils.searchDependencyUsages()
//
//        val mavenDomModel = MavenDomUtil.getMavenDomModel(fromPsiPom, MavenDomProjectModel::class.java) ?: return
//        val managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(mavenDomModel)
//
//        managedDependencies.forEach { println("key: " + it.key.artifactId + " value: " + it.value.artifactId) }
//        val ids = MavenArtifactSearchDialog.searchForArtifact(project, managedDependencies.values)
//        ids.forEach(::println)
        val mavenProjectManager = MavenProjectsManager.getInstance(project)


        val fromId = from.mavenId
        val intoId = into.mavenId
        if (from.parentId != into.parentId) {
            Notifications.Bus.notify(Notification("ModulesMerger", "Unable to merge modules", "modules should have the same parent", NotificationType.ERROR))
            return
        }

        val dependencies: MutableSet<MavenArtifact> = computeMergedDependencies(fromId, intoId)

        progressIndicator.fraction = 0.50

        writeDependencies(project, into, dependencies)

        progressIndicator.fraction = 1.0

        moveSources(from, into)
        updateReferences(fromId, intoId)
        removeModule(from)


//        def Node moduleToRemove = parentPom.modules.module.find {
//            it.text() == fromId
//        }
//        println "remove module ${moduleToRemove.text()} from pom $parentId"
//        moduleToRemove.replaceNode {
//
//        }
//        new File (into, "pom.xml").text = groovy.xml.XmlUtil.serialize(intoPom)
//        new File (into.parent, "pom.xml").text = groovy.xml.XmlUtil.serialize(parentPom)
//        println "remove dir $fromId"
//        deleteDir(from)
//        println "replace all dependencies on $fromGroup:$fromId by dependency on $intoGroup:$intoId"
//        replaceAllDependencies(base, fromGroup, fromId, intoGroup, intoId)
    }

    private fun updateReferences(fromId: MavenId, intoId: MavenId) {
    }

    private fun removeModule(from: MavenProject) {
    }

    private fun moveSources(from: MavenProject, into: MavenProject) {
        val srcFolder = from.file.parent.findChild("src")
        srcFolder ?: return
        srcFolder.move(null, into.file.parent)
    }

    private fun computeMergedDependencies(fromId: MavenId, intoId: MavenId): MutableSet<MavenArtifact> {
        val dependencies: MutableSet<MavenArtifact> = HashSet()
        //collect dependencies of 'to' pom
        dependencies.addAll(into.dependencyTree.map { it.artifact })
        //collect dependencies of 'from' pom
        dependencies.addAll(from.dependencyTree.map { it.artifact })
        dependencies.removeAll { it.mavenId == fromId }
        dependencies.removeAll { it.mavenId == intoId }
        return dependencies
    }

    private fun writeDependencies(project: Project, mavenProject: MavenProject, dependencies: MutableSet<MavenArtifact>) {
        val intoPsiFile = object : ReadAction<PsiFile?>() {
            override fun run(p0: Result<PsiFile?>) = p0.setResult(PsiManager.getInstance(project).findFile(mavenProject.file))

        }.execute().resultObject
        intoPsiFile ?: return
        val intoMavenModel = MavenDomUtil.getMavenDomModel(intoPsiFile, MavenDomProjectModel::class.java) ?: return
        object : WriteCommandAction<MavenDomDependency>(intoPsiFile.project, "Generate Dependency", intoPsiFile) {
            @Throws(Throwable::class)
            override fun run(result: Result<MavenDomDependency>) {
                val managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(intoMavenModel)
                for (each in dependencies) {
                    val conflictId = DependencyConflictId(each.groupId!!, each.artifactId!!, null, null)
                    val managedDependenciesDom = managedDependencies[conflictId]
                    createDependency(each, intoMavenModel, managedDependenciesDom)
                }
            }
        }.execute()
    }

    private fun createDependency(dependency: MavenArtifact, intoMavenModel: MavenDomProjectModel, managedDependenciesDom: MavenDomDependency?) {
        if (managedDependenciesDom != null && Comparing.equal(dependency.version, managedDependenciesDom.version.stringValue)) {
            // Generate dependency without <version> tag
            val res: MavenDomDependency = MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null)
            res.groupId.stringValue = dependency.groupId
            res.artifactId.stringValue = dependency.artifactId
            LOGGER.info("created dependency ${dependency.groupId}:${dependency.artifactId}")
        } else if (dependency.mavenId.version.equals(into.mavenId.version)) {
            val res: MavenDomDependency = MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null)
            res.groupId.stringValue = dependency.groupId
            res.artifactId.stringValue = dependency.artifactId
            res.version.stringValue = "\${project.version}"
            LOGGER.info("created dependency ${dependency.groupId}:${dependency.artifactId}:\${project.version}")
        } else {
            MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null, dependency.mavenId)
            LOGGER.info("created dependency ${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
        }
    }

}