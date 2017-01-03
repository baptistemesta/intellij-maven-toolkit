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
import com.intellij.openapi.vfs.VirtualFile
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

        moveSources(project, from, into)
        updateReferences(project, fromId, intoId)
        removeModule(project, from)
    }

    private fun updateReferences(project: Project, fromId: MavenId, intoId: MavenId) {
        //TODO
    }

    private fun removeModule(project: Project, from: MavenProject) {
        val intoPsiFile = getPsiFile(from.file.parent, project)
        object : WriteCommandAction<Any>(project, "Generate Dependency", intoPsiFile) {
            @Throws(Throwable::class)
            override fun run(result: Result<Any>) {
                from.file.parent.delete(null)
                //TODO update parent: remove from module list
            }
        }.execute()
    }

    private fun moveSources(project: Project, from: MavenProject, into: MavenProject) {
        val intoPsiFile = getPsiFile(from.file, project)
        val srcFolder = from.file.parent.findChild("src")
        srcFolder ?: return
        object : WriteCommandAction<Any>(project, "Generate Dependency", intoPsiFile) {
            @Throws(Throwable::class)
            override fun run(result: Result<Any>) {
                copyInto(srcFolder, into.file.parent)
            }
        }.execute()
    }

    private fun getPsiFile(file: VirtualFile, project: Project): PsiFile? {
        val intoPsiFile = object : ReadAction<PsiFile?>() {
            override fun run(p0: Result<PsiFile?>) {
                p0.setResult(PsiManager.getInstance(project).findFile(file))
            }

        }.execute().resultObject
        return intoPsiFile
    }

    private fun copyInto(src: VirtualFile, intoFolder: VirtualFile) {
        val target = intoFolder.findChild(src.name)
        if (target == null) {
            src.move(null, intoFolder)
            return
        }
        if (src.isDirectory) {
            src.children.forEach {
                copyInto(it, target)
            }
            src.delete(null)
            return
        }
        println("error ${src.name} already exists")
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