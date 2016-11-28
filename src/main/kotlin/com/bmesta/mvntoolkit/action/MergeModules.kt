package com.bmesta.mvntoolkit.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction

/**
 * @author Baptiste Mesta
 */
class MergeModules() : BaseRefactoringAction() {
    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean {
        for (element in elements) {
            println(element)
        }
        return elements.size == 2
    }

    override fun getHandler(p0: DataContext): RefactoringActionHandler? {
        return MergeModulesHandler()
    }

    override fun isAvailableInEditorOnly(): Boolean {
        return false
    }

    //private fun available(e: AnActionEvent): Boolean = MavenActionUtil.getMavenProjects(e.dataContext).size == 2
}

