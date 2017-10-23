/*
 *  Copyright 2017 [name of copyright owner]
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.bmesta.mvntoolkit.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
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
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectReader
import org.jetbrains.idea.maven.project.MavenProjectReaderProjectLocator
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.HashSet


/**
 * @author Baptiste Mesta
 */
class ModulesMerger(val project: Project, val progressIndicator: ProgressIndicator) {
    private val LOGGER = Logger.getInstance("#com.bmesta.mvntoolkit.action.ModulesMerger")
    /**
     * Tracks modules that have been merged in, so we don't try to merge them twice
     */
    private val mergedModules = mutableSetOf<MavenId>()

    /**
     * Given a VirtualFile referencing a pom.xml, create a MavenProject
     */
    private fun hydrateMavenProject(file: VirtualFile): MavenProject {
        val mavenProject = MavenProject(file)
        val generalSettings = MavenProjectsManager.getInstance(project).generalSettings
        val locator = MavenProjectReaderProjectLocator { null }
        val reader = MavenProjectReader(project)
        // initialize maven project
        mavenProject.read(generalSettings, MavenExplicitProfiles.NONE, reader, locator)

        return mavenProject
    }

    /**
     * Recursively merge Maven projects and their modules
     */
    fun merge(mavenProjects: List<MavenProject>): MavenProject {
        return mavenProjects.reduce { from, into ->

            val fromResult = if (from.existingModuleFiles.isNotEmpty()) {
                // Merge child modules, then merge the result into the parent
                val modules = from.existingModuleFiles.map { hydrateMavenProject(it) }
                merge(modules + from)
            } else {
                from
            }

            if (mergedModules.contains(into.mavenId)) {
                // If into was already merged, ignore it
                fromResult
            } else {
                val intoResult = if (into.existingModuleFiles.isNotEmpty()
                    // Don't recurse if we are merging the last child into the parent
                    && !into.existingModuleFiles.contains(from.file)) {

                    // Merge child modules, then merge the result into the parent
                    val modules = into.existingModuleFiles.map { hydrateMavenProject(it) }
                    merge(modules + into)
                } else {
                    into
                }

                doMerge(fromResult, intoResult)
            }
        }
    }

    private fun doMerge(from: MavenProject, into: MavenProject): MavenProject {
        Notifications.Bus.notify(Notification("intellij-maven-toolkit", "Merging", "Merging ${from.name} into ${into.name}", NotificationType.INFORMATION))
        incrementProgress()
        val dependencies: MutableSet<MavenArtifact> = computeMergedDependencies(from, into)
        incrementProgress()
        writeDependencies(project, into, dependencies)
        incrementProgress()
        moveSources(project, from, into)
        incrementProgress()
        updateReferences(project, from.mavenId, into.mavenId)
        incrementProgress()
        removeModule(project, from)
        incrementProgress()

        mergedModules.add(from.mavenId)
        return into
    }

    private fun incrementProgress() {
        progressIndicator.fraction += 0.15
    }


    private fun updateReferences(project: Project, fromId: MavenId, intoId: MavenId) {
        val projectFileIndex =
            ServiceManager.getService(project, ProjectFileIndex::class.java)
        projectFileIndex.iterateContent { currentFile ->
            if (currentFile.name == "pom.xml") {
                val currentPomPsi = getPsiFile(currentFile, project) ?: return@iterateContent true
                replaceDependencyInPom(currentPomPsi, fromId, intoId)
            }
            true
        }

    }

    private fun replaceDependencyInPom(currentPomPsi: PsiFile, fromId: MavenId, intoId: MavenId) {
        write("Update dependencies", currentPomPsi) {
            val mavenDomModel = getMavenDomModel(currentPomPsi)
            mavenDomModel?.dependencies?.dependencies?.forEach {
                if (it.artifactId.stringValue == fromId.artifactId && it.groupId.stringValue == fromId.groupId) {
                    it.artifactId.stringValue = intoId.artifactId
                    it.groupId.stringValue = intoId.groupId
                }
            }
        }
    }

    private fun getMavenDomModel(currentPomPsi: PsiFile) = read { MavenDomUtil.getMavenDomModel(currentPomPsi, MavenDomProjectModel::class.java) }

    private fun removeModule(project: Project, from: MavenProject) {
        val parentPomFile = from.file.parent.parent.findChild("pom.xml")
        parentPomFile ?: return
        val parentPomPsi = getPsiFile(parentPomFile, project)
        parentPomPsi ?: return
        write("Delete module in parent", parentPomPsi) {
            val mavenDomModel = getMavenDomModel(parentPomPsi)
            val indexInModules = mavenDomModel?.modules?.modules?.indexOfFirst { it.stringValue == from.mavenId.artifactId }
            if (indexInModules != null) {
                removeModuleAt(indexInModules, mavenDomModel)
                if (hasNoModules(mavenDomModel)) {
                    deleteModuleTag(mavenDomModel)
                }
            }
        }
        write("delete the module") {
            from.file.parent.delete(null)
        }
    }

    private fun removePackaging(mavenDomModel: MavenDomProjectModel?) {
        mavenDomModel?.packaging?.xmlTag?.delete()
    }

    private fun isPomPackaging(mavenDomModel: MavenDomProjectModel?) = "pom" == mavenDomModel?.packaging?.rawText ?: ""

    private fun removeModuleAt(indexInModules: Int, mavenDomModel: MavenDomProjectModel?) {
        mavenDomModel?.modules?.xmlTag?.subTags?.get(indexInModules)?.delete()
    }

    private fun deleteModuleTag(mavenDomModel: MavenDomProjectModel?) {
        mavenDomModel?.modules?.xmlTag?.delete()
    }

    private fun hasNoModules(mavenDomModel: MavenDomProjectModel?) = mavenDomModel?.modules?.modules?.size == 0

    private fun moveSources(project: Project, from: MavenProject, into: MavenProject) {
        val intoPsiFile = getPsiFile(from.file, project)
        val srcFolder = from.file.parent.findChild("src")
        srcFolder ?: return
        intoPsiFile ?: return
        write("move sources", intoPsiFile) { copyInto(srcFolder, into.file.parent) }

    }

    private fun getPsiFile(file: VirtualFile, project: Project): PsiFile? {
        return read { psiFile(file, project) }
    }

    private fun psiFile(file: VirtualFile, project: Project): PsiFile? = PsiManager.getInstance(project).findFile(file)

    private fun <T> read(body: () -> T?): T? {
        return object : ReadAction<T>() {
            override fun run(p0: Result<T>) {
                p0.setResult(body())
            }

        }.execute().resultObject
    }

    private fun <T> write(name: String, vararg file: PsiFile, body: () -> T) {
        object : WriteCommandAction<Any>(project, name, *file) {
            @Throws(Throwable::class)
            override fun run(result: Result<Any>) {
                body()
            }
        }.execute()
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

    private fun computeMergedDependencies(from: MavenProject, into: MavenProject): MutableSet<MavenArtifact> {
        val dependencies: MutableSet<MavenArtifact> = HashSet()
        //collect dependencies of 'to' pom
        dependencies.addAll(into.dependencyTree.map { it.artifact })
        //collect dependencies of 'from' pom
        dependencies.addAll(from.dependencyTree.map { it.artifact })
        dependencies.removeAll { it.mavenId == from.mavenId }
        dependencies.removeAll { it.mavenId == into.mavenId }
        return dependencies
    }

    private fun writeDependencies(project: Project, mavenProject: MavenProject, dependencies: MutableSet<MavenArtifact>) {
        val intoPsiFile = getPsiFile(mavenProject.file, project)
        intoPsiFile ?: return
        val intoMavenModel = getMavenDomModel(intoPsiFile)
        intoMavenModel ?: return
        write("Write new dependencies", intoPsiFile) {
            val managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(intoMavenModel)
            for (each in dependencies) {
                val conflictId = DependencyConflictId(each.groupId!!, each.artifactId!!, null, null)
                val managedDependenciesDom = managedDependencies[conflictId]
                createDependency(each, mavenProject, intoMavenModel, managedDependenciesDom)
            }
            if (isPomPackaging(intoMavenModel)) {
                removePackaging(intoMavenModel)
            }
        }
    }

    private fun createDependency(dependency: MavenArtifact, mavenProject: MavenProject, intoMavenModel: MavenDomProjectModel, managedDependenciesDom: MavenDomDependency?) {
        if (managedDependenciesDom != null && Comparing.equal(dependency.version, managedDependenciesDom.version.stringValue)) {
            // Generate dependency without <version> tag
            val res: MavenDomDependency = MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null)
            res.groupId.stringValue = dependency.groupId
            res.artifactId.stringValue = dependency.artifactId
        } else if (dependency.mavenId.version.equals(mavenProject.mavenId.version)) {
            val res: MavenDomDependency = MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null)
            res.groupId.stringValue = dependency.groupId
            res.artifactId.stringValue = dependency.artifactId
            res.version.stringValue = "\${project.version}"
        } else {
            MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null, dependency.mavenId)
        }
    }

}
