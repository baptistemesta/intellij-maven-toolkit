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

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProject

/**
 * @author Baptiste Mesta
 */
class ModulesMergerTask(project: Project, val from: MavenProject, val into: MavenProject) : Task.Backgroundable(project, "Merging modules...") {
    override fun run(progressIndicator: ProgressIndicator) {

        ModulesMerger(project, from, into).merge(progressIndicator)

    }
}