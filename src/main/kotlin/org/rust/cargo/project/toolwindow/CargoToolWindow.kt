/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import org.rust.cargo.project.model.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JEditorPane

class CargoToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        guessAndSetupRustProject(project)
        val toolwindowPanel = CargoToolWindowPanel(project)
        val tab = ContentFactory.SERVICE.getInstance()
            .createContent(toolwindowPanel, "", false)
        toolWindow.contentManager.addContent(tab)
    }
}

private class CargoToolWindowPanel(project: Project) : SimpleToolWindowPanel(true, false) {
    private val cargoTab = CargoToolWindow(project)

    init {
        setToolbar(cargoTab.toolbar.component)
        cargoTab.toolbar.setTargetComponent(this)
        setContent(cargoTab.content)
    }

    override fun getData(dataId: String): Any? {
        if (DetachCargoProjectAction.CARGO_PROJECT_TO_DETACH.`is`(dataId)) {
            return cargoTab.selectedProject
        }
        return super.getData(dataId)
    }
}

private class CargoToolWindow(
    private val project: Project
) {
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("Cargo Toolbar", actionManager.getAction("Rust.Cargo") as DefaultActionGroup, true)
    }

    val note = JEditorPane("text/html", html("")).apply {
        background = UIUtil.getTreeBackground()
        isEditable = false
    }

    private val projectStructure = CargoProjectStructure()
    private val projectTree = CargoProjectStructureTree(projectStructure).apply {
        cellRenderer = CargoProjectTreeRenderer()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val isDoubleClick = e.clickCount == 2
            }
        })
    }

    val selectedProject: CargoProject? get() = projectTree.selectedProject

    val content: JComponent = ScrollPaneFactory.createScrollPane(projectTree, 0)

    init {
        with(project.messageBus.connect()) {
            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                    ApplicationManager.getApplication().invokeLater {
                        projectStructure.updateCargoProjects(projects.sortedBy { it.manifest })
                    }
                }
            })
        }

        ApplicationManager.getApplication().invokeLater {
            projectStructure.updateCargoProjects(project.cargoProjects.allProjects.sortedBy { it.manifest })
        }
    }

    private fun html(body: String): String = """
        <html>
        <head>
            ${UIUtil.getCssFontDeclaration(UIUtil.getLabelFont())}
            <style>body {background: #${ColorUtil.toHex(UIUtil.getTreeBackground())}; text-align: center; }</style>
        </head>
        <body>
            $body
        </body>
        </html>
    """
}
