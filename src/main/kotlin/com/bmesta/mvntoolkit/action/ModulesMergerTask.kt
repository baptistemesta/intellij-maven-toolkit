package com.bmesta.mvntoolkit.action

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProject

/**
 * @author Baptiste Mesta
 */
class ModulesMergerTask(project: Project, val from: MavenProject, val into: MavenProject) : Task.Backgroundable(project, "Merging modules...") {
    override fun run(p0: ProgressIndicator) {
        ModulesMerger(from, into).merge()
    }
}