package app.termora


import app.termora.actions.*
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereResult
import app.termora.terminal.DataKey
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatButton
import com.formdev.flatlaf.extras.components.FlatTextField
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.math.max

class WelcomePanel(private val windowScope: WindowScope) : JPanel(BorderLayout()), Disposable, TerminalTab,
    DataProvider {
    private val properties get() = Database.getDatabase().properties
    private val rootPanel = JPanel(BorderLayout())
    private val searchTextField = FlatTextField()
    private val hostTree = HostTree()
    private val bannerPanel = BannerPanel()
    private val toggle = FlatButton()
    private var fullContent = properties.getString("WelcomeFullContent", "false").toBoolean()
    private val dataProviderSupport = DataProviderSupport()

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, false)

        val panel = JPanel(BorderLayout())
        panel.add(createSearchPanel(), BorderLayout.NORTH)
        panel.add(createHostPanel(), BorderLayout.CENTER)

        if (!fullContent) {
            rootPanel.add(bannerPanel, BorderLayout.NORTH)
        }

        rootPanel.add(panel, BorderLayout.CENTER)
        add(rootPanel, BorderLayout.CENTER)

        dataProviderSupport.addData(DataProviders.Welcome.HostTree, hostTree)

    }

    private fun createSearchPanel(): JComponent {
        searchTextField.focusTraversalKeysEnabled = false
        searchTextField.preferredSize = Dimension(
            searchTextField.preferredSize.width,
            (UIManager.getInt("TitleBar.height") * 0.85).toInt()
        )


        val iconSize = (searchTextField.preferredSize.height * 0.65).toInt()

        val newHost = FlatButton()
        newHost.icon = FlatSVGIcon(
            Icons.openNewTab.name,
            iconSize,
            iconSize
        )
        newHost.isFocusable = false
        newHost.buttonType = FlatButton.ButtonType.toolBarButton
        newHost.addActionListener { e ->
            ActionManager.getInstance().getAction(NewHostAction.NEW_HOST)?.actionPerformed(e)
        }


        toggle.icon = FlatSVGIcon(
            if (fullContent) Icons.collapseAll.name else Icons.collapseAll.name,
            iconSize,
            iconSize
        )
        toggle.isFocusable = false
        toggle.buttonType = FlatButton.ButtonType.toolBarButton

        val box = Box.createHorizontalBox()
        box.add(searchTextField)
        box.add(Box.createHorizontalStrut(4))
        box.add(newHost)
        box.add(Box.createHorizontalStrut(4))
        box.add(toggle)

        if (!fullContent) {
            box.border = BorderFactory.createEmptyBorder(20, 0, 0, 0)
        }

        toggle.addActionListener {
            fullContent = !fullContent
            toggle.icon = FlatSVGIcon(
                if (fullContent) Icons.collapseAll.name else Icons.collapseAll.name,
                iconSize,
                iconSize
            )
            if (fullContent) {
                box.border = BorderFactory.createEmptyBorder()
            } else {
                box.border = BorderFactory.createEmptyBorder(20, 0, 0, 0)
            }
            perform()
        }

        return box
    }

    private fun createHostPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        hostTree.actionMap.put("find", object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                searchTextField.requestFocusInWindow()
            }
        })
        hostTree.showsRootHandles = true

        Disposer.register(this, hostTree)

        val scrollPane = JScrollPane(hostTree)
        scrollPane.verticalScrollBar.maximumSize = Dimension(0, 0)
        scrollPane.verticalScrollBar.preferredSize = Dimension(0, 0)
        scrollPane.verticalScrollBar.minimumSize = Dimension(0, 0)
        scrollPane.border = BorderFactory.createEmptyBorder()


        panel.add(scrollPane, BorderLayout.CENTER)
        panel.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)


        return panel
    }


    private fun initEvents() {

        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) {
                if (!searchTextField.hasFocus()) {
                    searchTextField.requestFocusInWindow()
                }
                perform()
                removeComponentListener(this)
            }
        })


        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                perform()
            }
        })


        FindEverywhereProvider.getFindEverywhereProviders(windowScope)
            .add(object : FindEverywhereProvider {
                override fun find(pattern: String): List<FindEverywhereResult> {
                    var filter = TreeUtils.children(hostTree.model, hostTree.model.root)
                        .filterIsInstance<Host>()
                        .filter { it.protocol != Protocol.Folder }

                    if (pattern.isNotBlank()) {
                        filter = filter.filter {
                            if (it.protocol == Protocol.SSH) {
                                it.name.contains(pattern, true) || it.host.contains(pattern, true)
                            } else {
                                it.name.contains(pattern, true)
                            }
                        }
                    }

                    return filter.map { HostFindEverywhereResult(it) }
                }

                override fun group(): String {
                    return I18n.getString("termora.find-everywhere.groups.open-new-hosts")
                }

                override fun order(): Int {
                    return Integer.MIN_VALUE + 2
                }
            })

        searchTextField.document.addDocumentListener(object : DocumentAdaptor() {
            private var state = StringUtils.EMPTY
            override fun changedUpdate(e: DocumentEvent) {
                val text = searchTextField.text
                if (text.isBlank()) {
                    hostTree.setModel(hostTree.model)
                    TreeUtils.loadExpansionState(hostTree, state)
                    state = String()
                } else {
                    if (state.isBlank()) state = TreeUtils.saveExpansionState(hostTree)
                    hostTree.setModel(hostTree.searchableModel)
                    hostTree.searchableModel.search(text)
                    TreeUtils.expandAll(hostTree)
                }
            }
        })
    }

    private fun perform() {
        rootPanel.remove(bannerPanel)
        if (fullContent) {
            rootPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        } else {
            val top = max((height * 0.08).toInt(), 30)
            val left = max((width * 0.25).toInt(), 30)
            rootPanel.add(bannerPanel, BorderLayout.NORTH)
            rootPanel.border = BorderFactory.createEmptyBorder(top, left, top / 2, left)
            SwingUtilities.invokeLater {
                rootPanel.revalidate()
                rootPanel.repaint()
            }
        }
    }


    override fun getTitle(): String {
        return I18n.getString("termora.title")
    }

    override fun getIcon(): Icon {
        return Icons.homeFolder
    }

    override fun getJComponent(): JComponent {
        return this
    }

    override fun canReconnect(): Boolean {
        return false
    }

    override fun canClose(): Boolean {
        return false
    }

    override fun canClone(): Boolean {
        return false
    }

    override fun dispose() {
        hostTree.setModel(null)
        properties.putString("WelcomeFullContent", fullContent.toString())
    }

    private inner class HostFindEverywhereResult(val host: Host) : FindEverywhereResult {
        private val showMoreInfo get() = properties.getString("HostTree.showMoreInfo", "false").toBoolean()

        override fun actionPerformed(e: ActionEvent) {
            ActionManager.getInstance()
                .getAction(OpenHostAction.OPEN_HOST)
                ?.actionPerformed(OpenHostActionEvent(e.source, host, e))
        }

        override fun getIcon(isSelected: Boolean): Icon {
            if (isSelected) {
                if (!FlatLaf.isLafDark()) {
                    return Icons.terminal.dark
                }
            }
            return Icons.terminal
        }

        override fun getText(isSelected: Boolean): String {
            if (showMoreInfo) {
                val color = UIManager.getColor(if (isSelected) "textHighlightText" else "textInactiveText")
                val moreInfo = if (host.protocol == Protocol.SSH) {
                    "${host.username}@${host.host}"
                } else if (host.protocol == Protocol.Serial) {
                    host.options.serialComm.port
                } else {
                    StringUtils.EMPTY
                }
                if (moreInfo.isNotBlank()) {
                    return "<html>${host.name}&nbsp;&nbsp;&nbsp;&nbsp;<font color=rgb(${color.red},${color.green},${color.blue})>${moreInfo}</font></html>"
                }
            }
            return host.name
        }
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return dataProviderSupport.getData(dataKey)
    }


}