package com.bmesta.mvntoolkit.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import org.jetbrains.idea.maven.project.MavenProject

/**
 * @author Baptiste Mesta
 */
class ModulesMerger(val from: MavenProject, val into: MavenProject) {


    fun merge() {
//
//        def fromPom = new XmlParser().parseText(new File(from, "pom.xml").text)
//        def intoPom = new XmlParser().parseText(new File(into, "pom.xml").text)
//        def parentPom = new XmlParser().parseText(new File(into.parent, "pom.xml").text)


        val fromId = from.mavenId
        val intoId = into.mavenId
        val parentId = from.parentId
        if (from.parentId != into.parentId) {
            Notifications.Bus.notify(Notification("ModulesMerger", "Unable to merge modules", "modules should have the same parent", NotificationType.ERROR))
            return
        }

        println("copy source folder of $fromId into $intoId")

        //copy(new File (from, "src"), new File(into, "src"))
//        val dependencies : Set<Dependency> = Set<Dependency>
//        dependencies.addAll(getDependencies(intoPom))
//        dependencies.addAll(getDependencies(fromPom))
//
//        intoPom.dependencies.replaceNode {
//        }
//
//        def dependenciesNode = intoPom . appendNode ("dependencies")
//
//        println "process new dependencies into $intoId"
//        dependencies.each { dependency ->
//            println "add dependency $dependency"
//            addDependency(dependenciesNode, dependency)
//        }
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
}