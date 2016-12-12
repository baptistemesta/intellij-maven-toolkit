package com.bmesta.mvntoolkit.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.util.IncorrectOperationException
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil

class MergeModulesHandler : RefactoringActionHandler {


    private val LOG = Logger.getInstance("#com.bmesta.mvntoolkit.action.MergeModulesHandler")

    override fun invoke(p0: Project, p1: Editor?, p2: PsiFile?, p3: DataContext?) {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, context: DataContext?) {

        val mavenProjects = MavenActionUtil.getMavenProjects(context)
        val from = mavenProjects[0]
        val into = mavenProjects[1]
        if (from != null && into != null) {
            if (showRefactoringDialog()) {
                CommandProcessor.getInstance().executeCommand(project, {
                    val action = {
                        try {
                            doRefactoring(project, from, into)
                        } catch (var2: IncorrectOperationException) {
                            LOG.error(var2)
                        }
                    }
                    ApplicationManager.getApplication().runWriteAction(action)
                }, "Merge modules", null)
            }
        }

    }

    private fun doRefactoring(projct: Project, from: MavenProject, into: MavenProject) {
        ModulesMerger(projct, from, into).merge()
    }

    private fun showRefactoringDialog(): Boolean {
        //no dialog yet
        return true
    }

}