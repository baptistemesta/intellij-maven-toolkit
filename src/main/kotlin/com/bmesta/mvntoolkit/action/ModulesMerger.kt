package com.bmesta.mvntoolkit.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import org.jetbrains.idea.maven.dom.DependencyConflictId
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.generate.GenerateManagedDependencyAction
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.*

/**
 * @author Baptiste Mesta
 */
class ModulesMerger(val project: Project, val from: MavenProject, val into: MavenProject) {


    fun merge() {
//
//        def fromPom = new XmlParser().parseText(new File(from, "pom.xml").text)
//        def intoPom = new XmlParser().parseText(new File(into, "pom.xml").text)
//        def parentPom = new XmlParser().parseText(new File(into.parent, "pom.xml").text)
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
        val parentId = from.parentId
        if (from.parentId != into.parentId) {
            Notifications.Bus.notify(Notification("ModulesMerger", "Unable to merge modules", "modules should have the same parent", NotificationType.ERROR))
            return
        }

        val dependencies: MutableSet<MavenArtifact> = HashSet()
        //collect dependencies of 'to' pom
        dependencies.addAll(into.dependencies)
        //collect dependencies of 'from' pom
        dependencies.addAll(from.dependencies)
        dependencies.removeAll { it.mavenId == fromId }
        dependencies.removeAll { it.mavenId == intoId }

        writeDependencies(project, into, dependencies)


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

    private fun writeDependencies(project: Project, mavenProject: MavenProject, dependencies: MutableSet<MavenArtifact>) {

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val intoPsiFile = PsiManager.getInstance(project).findFile(mavenProject.file) ?: return
        val intoMavenModel = MavenDomUtil.getMavenDomModel(intoPsiFile, MavenDomProjectModel::class.java) ?: return
        val resultObject = object : WriteCommandAction<MavenDomDependency>(intoPsiFile.project, "Generate Dependency", intoPsiFile) {
            @Throws(Throwable::class)
            override fun run(result: Result<MavenDomDependency>) {
                val managedDependencies = GenerateManagedDependencyAction.collectManagingDependencies(intoMavenModel)
                for (each in dependencies) {
                    val res: MavenDomDependency
                    val conflictId = DependencyConflictId(each.groupId!!, each.artifactId!!, null, null)
                    val managedDependenciesDom = managedDependencies[conflictId]
                    if (managedDependenciesDom != null && Comparing.equal(each.version, managedDependenciesDom.version.stringValue)) {
                        // Generate dependency without <version> tag
                        res = MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null)
                        res.groupId.stringValue = conflictId.groupId
                        res.artifactId.stringValue = conflictId.artifactId
                    } else {
                        //TODO use project.version when necessary
                        res = MavenDomUtil.createDomDependency(intoMavenModel.dependencies, null, each.mavenId)
                    }
                    result.setResult(res)
                }
            }
        }.execute().resultObject

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        println("result is " + resultObject)
    }

}