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

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
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
        if (mavenProjects.none { it == null }) {
            if (showRefactoringDialog()) {
                CommandProcessor.getInstance().executeCommand(project, {
                    val action = {
                        try {
                            doRefactoring(project, mavenProjects)
                        } catch (var2: IncorrectOperationException) {
                            LOG.error(var2)
                        }
                    }
                    ApplicationManager.getApplication().runWriteAction(action)
                }, "Merge modules", null)
            }
        }

    }

    private fun doRefactoring(project: Project, mavenProjects: List<MavenProject>) {

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        ModulesMergerTask(project, mavenProjects).queue()

        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun showRefactoringDialog(): Boolean {
        //no dialog yet
        return true
    }

}
