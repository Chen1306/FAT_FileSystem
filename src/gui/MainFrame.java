package gui;

import fs.BootBlock;
import fs.DirItem;
import fs.FileSystem;
import fs.OFT;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 主窗口：包含目录树、文件列表、日志区、右键菜单
 */
public class MainFrame extends JFrame {
    private final FileSystem fs;
    private String currentPath = "/";
    private final DefaultMutableTreeNode rootNode =
            new DefaultMutableTreeNode(new DirNode("/", "根目录"));
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final FileTableModel tableModel = new FileTableModel();
    private final JTable table;
    private final JTextArea output = new JTextArea();
    private final JLabel pathLabel = new JLabel();
    private final List<DirItem> currentItems = new ArrayList<>();
    private DiskDialog diskDialog;

    //文件主窗口
    public MainFrame(FileSystem fs) {
        this.fs = fs;
        setTitle("简易 FAT 文件系统模拟器");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        //左侧目录树
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(e -> onTreeSelected());
        tree.addMouseListener(new TreeMouseHandler());

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new TableMouseHandler());

        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        pathLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JButton diskButton = new JButton("磁盘管理");
        diskButton.addActionListener(e -> showDiskDialog());
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.add(pathLabel, BorderLayout.CENTER);
        topBar.add(diskButton, BorderLayout.EAST);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(topBar, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tablePanel, new JScrollPane(output));
        rightSplit.setResizeWeight(0.55);
        rightSplit.setDividerLocation(320);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), rightSplit);
        mainSplit.setResizeWeight(0.25);
        mainSplit.setDividerLocation(220);
        add(mainSplit, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    fs.close();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "关闭磁盘失败: " + ex.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                }
                dispose();
                System.exit(0);
            }
        });

        refreshTree();
        refreshTable();
        appendLog("文件系统已启动。右键目录树创建目录与创建文件，双击文件写入内容。");
    }

    private void refreshTree() {
        rootNode.removeAllChildren();
        try {
            buildTree(rootNode, "/");
        } catch (Exception e) {
            showError("刷新目录树失败: " + e.getMessage());
        }
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        DefaultMutableTreeNode target = findNode(rootNode, currentPath);
        if (target == null) {
            currentPath = "/";
            target = rootNode;
        }
        TreePath treePath = new TreePath(target.getPath());
        tree.setSelectionPath(treePath);
        tree.scrollPathToVisible(treePath);
        pathLabel.setText("当前目录: " + currentPath);
    }

    private void buildTree(DefaultMutableTreeNode parent, String path) throws Exception {
        for (DirItem item : fs.listDirectory(path)) {
            if (!item.isDirectory()) {
                continue;
            }
            String childPath = join(path, item.getName());
            DefaultMutableTreeNode child =
                    new DefaultMutableTreeNode(new DirNode(childPath, item.getName()));
            parent.add(child);
            buildTree(child, childPath);
        }
    }

    private DefaultMutableTreeNode findNode(DefaultMutableTreeNode node, String path) {
        Object user = node.getUserObject();
        if (user instanceof DirNode && ((DirNode) user).path.equals(path)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found =
                    findNode((DefaultMutableTreeNode) node.getChildAt(i), path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void onTreeSelected() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) {
            return;
        }
        Object user = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
        if (user instanceof DirNode) {
            currentPath = ((DirNode) user).path;
            pathLabel.setText("当前目录: " + currentPath);
            refreshTable();
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        currentItems.clear();
        try {
            currentItems.addAll(fs.listDirectory(currentPath));
            for (DirItem item : currentItems) {
                tableModel.addRow(new Object[]{
                        item.getName(),
                        item.getTypeText(),
                        String.format("0x%02X", item.getAttribute()),
                        item.getLength(),
                        item.getStartBlock()
                });
            }
        } catch (Exception e) {
            showError("刷新目录失败: " + e.getMessage());
        }
    }

    private void showDiskDialog() {
        if (diskDialog != null && diskDialog.isShowing()) {
            diskDialog.toFront();
            return;
        }
        diskDialog = new DiskDialog(this, fs);
        diskDialog.setVisible(true);
    }

    private void navigateTo(String path) {
        DefaultMutableTreeNode node = findNode(rootNode, path);
        if (node != null) {
            TreePath treePath = new TreePath(node.getPath());
            tree.setSelectionPath(treePath);
            tree.scrollPathToVisible(treePath);
        }
    }

    private String promptName(String title) {
        String name = JOptionPane.showInputDialog(this,
                "请输入名字（1~3 个字母或数字，不能包含 $ . /）：",
                title, JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return null;
        }
        name = name.trim();
        if (!FileSystem.isValidName(name)) {
            showError("非法名字: " + name
                    + "（长度 1~3，只能使用字母或数字，不能使用 $ . /）");
            return null;
        }
        return name;
    }

    private String promptContent(String path) {
        String existing;
        try {
            existing = fs.previewFile(path);
        } catch (Exception ex) {
            showError("读取现有内容失败: " + ex.getMessage());
            return null;
        }
        JTextArea existingArea = new JTextArea(existing);
        existingArea.setEditable(false);
        existingArea.setLineWrap(true);
        existingArea.setWrapStyleWord(true);
        JScrollPane existingPane = new JScrollPane(existingArea);
        existingPane.setPreferredSize(new Dimension(480, 180));
        existingPane.setBorder(BorderFactory.createTitledBorder("现有内容（只读）"));

        JTextArea inputArea = new JTextArea(8, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        JScrollPane inputPane = new JScrollPane(inputArea);
        inputPane.setPreferredSize(new Dimension(480, 140));
        inputPane.setBorder(BorderFactory.createTitledBorder("追加内容"));

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(existingPane, BorderLayout.CENTER);
        panel.add(inputPane, BorderLayout.SOUTH);

        int result = JOptionPane.showOptionDialog(this, panel,
                "写入文件 - " + path, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null,
                new Object[]{"保存", "取消"}, "保存");
        return result == 0 ? inputArea.getText() : null;
    }

    private Integer promptFileAttribute() {
        Object[] options = {"普通文件", "系统文件"};
        int choice = JOptionPane.showOptionDialog(this, "请选择文件属性", "新建文件",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0) {
            return null;
        }
        return choice == 0 ? BootBlock.ATTR_NORMAL : BootBlock.ATTR_SYSTEM;
    }

    private void createFileDialog(String dirPath) {
        String name = promptName("新建文件");
        if (name == null) {
            return;
        }
        Integer attribute = promptFileAttribute();
        if (attribute == null) {
            return;
        }
        doAction("新建文件", () -> {
            fs.createFile(join(dirPath, name), attribute);
            refreshTable();
        });
    }

    private void deleteDirectoryDialog(String path) {
        doAction("删除目录 " + path, () -> {
            fs.rd(path);
            if (currentPath.equals(path) || currentPath.startsWith(path + "/")) {
                currentPath = parentPath(path);
            }
            refreshTree();
            refreshTable();
        });
    }

    private void changeAttributeDialog(String path, String name) {
        Object[] options = {"普通文件", "系统文件", "只读文件"};
        int choice = JOptionPane.showOptionDialog(this, "请选择新属性", "修改属性",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0) {
            return;
        }
        int attribute = choice == 0 ? BootBlock.ATTR_NORMAL
                : choice == 1 ? BootBlock.ATTR_SYSTEM : BootBlock.ATTR_READONLY;
        doAction("修改属性 " + name, () -> {
            fs.changeAttribute(path, attribute);
            refreshTable();
        });
    }

    private void writeFileDialog(DirItem item) {
        String path = join(currentPath, item.getName());
        String content = promptContent(path);
        if (content == null) {
            return;
        }
        doAction("写入文件 " + item.getName(), () -> {
            fs.openFile(path, OFT.WRITE);
            try {
                fs.writeFile(path, content);
            } finally {
                fs.closeFile(path);
            }
            refreshTable();
        });
    }

    private void editFileDialog(String path, DirItem item) {
        if (item.isReadOnly()) {
            showError("只读文件不能编辑: " + path);
            return;
        }
        if (fs.isOpen(path)) {
            showError("文件已打开，请先关闭: " + path);
            return;
        }
        String content;
        try {
            content = fs.previewFile(path);
        } catch (Exception ex) {
            showError("读取文件失败: " + ex.getMessage());
            return;
        }

        JTextArea area = new JTextArea(content);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 420));

        JDialog dialog = new JDialog(this, "编辑文件 - " + path, true);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton save = new JButton("保存");
        JButton cancel = new JButton("取消");
        buttons.add(save);
        buttons.add(cancel);

        save.addActionListener(a -> {
            try {
                fs.rewriteFile(path, area.getText());
            } catch (Exception ex) {
                String message = "保存失败: " + ex.getMessage();
                appendLog("错误: " + message);
                JOptionPane.showMessageDialog(dialog, message, "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            appendLog("保存文件 " + path + "：成功");
            refreshTable();
            dialog.dispose();
        });
        cancel.addActionListener(a -> dialog.dispose());

        dialog.add(new JLabel(" 提示：内容不能包含字符 #"), BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(720, 520);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void doAction(String title, FsAction action) {
        try {
            action.run();
            appendLog(title + "：成功");
        } catch (Exception ex) {
            showError(title + "失败: " + ex.getMessage());
        }
    }

    private void showTextWindow(String title, String content) {
        JDialog dialog = new JDialog(this, title, false);
        JTextArea area = new JTextArea(content);
        area.setEditable(false);
        area.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.setSize(640, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void appendLog(String message) {
        output.append(message + "\n");
        output.setCaretPosition(output.getDocument().getLength());
    }

    private void showError(String message) {
        appendLog("错误: " + message);
        JOptionPane.showMessageDialog(this, message, "错误", JOptionPane.ERROR_MESSAGE);
    }

    private static String join(String dir, String name) {
        return "/".equals(dir) ? "/" + name : dir + "/" + name;
    }

    private static String parentPath(String path) {
        int index = path.lastIndexOf('/');
        return index <= 0 ? "/" : path.substring(0, index);
    }

    private interface FsAction {
        void run() throws Exception;
    }

    private static class DirNode {
        final String path;
        final String name;

        DirNode(String path, String name) {
            this.path = path;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class TreeMouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return;
            }
            TreePath path = tree.getPathForLocation(e.getX(), e.getY());
            if (path == null) {
                return;
            }
            tree.setSelectionPath(path);
            Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (!(user instanceof DirNode)) {
                return;
            }
            DirNode dir = (DirNode) user;

            JPopupMenu menu = new JPopupMenu();
            JMenuItem newDir = new JMenuItem("新建目录");
            newDir.addActionListener(a -> {
                String name = promptName("新建目录");
                if (name == null) {
                    return;
                }
                doAction("新建目录", () -> {
                    fs.md(join(dir.path, name));
                    refreshTree();
                    refreshTable();
                });
            });
            menu.add(newDir);

            JMenuItem newFile = new JMenuItem("新建文件");
            newFile.addActionListener(a -> createFileDialog(dir.path));
            menu.add(newFile);

            JMenuItem deleteDir = new JMenuItem("删除目录");
            deleteDir.setEnabled(!"/".equals(dir.path));
            deleteDir.addActionListener(a -> deleteDirectoryDialog(dir.path));
            menu.add(deleteDir);

            JMenuItem refresh = new JMenuItem("刷新");
            refresh.addActionListener(a -> {
                refreshTree();
                refreshTable();
            });
            menu.add(refresh);

            menu.show(tree, e.getX(), e.getY());
        }
    }

    private class TableMouseHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0 || row >= currentItems.size()) {
                    return;
                }
                DirItem item = currentItems.get(row);
                if (item.isDirectory()) {
                    navigateTo(join(currentPath, item.getName()));
                } else {
                    writeFileDialog(item);
                }
            }
        }

        private void maybeShowPopup(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return;
            }
            int row = table.rowAtPoint(e.getPoint());
            JPopupMenu menu = new JPopupMenu();
            if (row >= 0 && row < currentItems.size()) {
                table.setRowSelectionInterval(row, row);
                DirItem item = currentItems.get(row);
                if (item.isDirectory()) {
                    String path = join(currentPath, item.getName());
                    JMenuItem open = new JMenuItem("打开目录");
                    open.addActionListener(a -> navigateTo(path));
                    menu.add(open);

                    JMenuItem delete = new JMenuItem("删除目录");
                    delete.addActionListener(a -> deleteDirectoryDialog(path));
                    menu.add(delete);
                } else {
                    String path = join(currentPath, item.getName());

                    JMenuItem open = new JMenuItem("打开文件");
                    open.addActionListener(a -> {
                        Object[] options = {"读方式", "写方式"};
                        int choice = JOptionPane.showOptionDialog(MainFrame.this,
                                "请选择打开方式", "打开文件",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.QUESTION_MESSAGE,
                                null, options, options[0]);
                        if (choice < 0) {
                            return;
                        }
                        int flag = choice == 0 ? OFT.READ : OFT.WRITE;
                        doAction("打开文件 " + item.getName(),
                                () -> fs.openFile(path, flag));
                    });
                    menu.add(open);

                    JMenuItem read = new JMenuItem("读取文件");
                    read.addActionListener(a -> {
                        String input = JOptionPane.showInputDialog(MainFrame.this,
                                "请输入读取字节数：", "读取文件",
                                JOptionPane.PLAIN_MESSAGE);
                        if (input == null) {
                            return;
                        }
                        int length;
                        try {
                            length = Integer.parseInt(input.trim());
                        } catch (NumberFormatException ex) {
                            showError("读取字节数必须是整数");
                            return;
                        }
                        doAction("读取文件 " + item.getName(), () -> {
                            String content = fs.readFile(path, length);
                            appendLog("===== 读取 " + length + " 字节 =====");
                            appendLog(content.isEmpty() ? "(没有更多内容)" : content);
                        });
                    });
                    menu.add(read);

                    JMenuItem view = new JMenuItem("查看内容");
                    view.addActionListener(a -> doAction("查看内容", () -> {
                        String content = fs.typeFile(path);
                        showTextWindow("查看文件 - " + path, content);
                    }));
                    menu.add(view);

                    JMenuItem edit = new JMenuItem("编辑内容");
                    edit.addActionListener(a -> editFileDialog(path, item));
                    menu.add(edit);

                    JMenuItem write = new JMenuItem("写入内容");
                    write.addActionListener(a -> {
                        String content = promptContent(path);
                        if (content == null) {
                            return;
                        }
                        doAction("写入文件 " + item.getName(), () -> {
                            boolean wasOpen = fs.isOpen(path);
                            fs.openFile(path, OFT.WRITE);
                            try {
                                fs.writeFile(path, content);
                            } finally {
                                if (!wasOpen) {
                                    fs.closeFile(path);
                                }
                            }
                            if (wasOpen) {
                                appendLog("文件仍处于打开状态: " + path);
                            }
                            refreshTable();
                        });
                    });
                    menu.add(write);

                    JMenuItem close = new JMenuItem("关闭文件");
                    close.addActionListener(a -> doAction("关闭文件 " + item.getName(), () -> {
                        fs.closeFile(path);
                        refreshTable();
                    }));
                    menu.add(close);

                    JMenuItem change = new JMenuItem("修改属性");
                    change.addActionListener(a ->
                            changeAttributeDialog(path, item.getName()));
                    menu.add(change);

                    JMenuItem delete = new JMenuItem("删除文件");
                    delete.addActionListener(a ->
                            doAction("删除文件 " + item.getName(), () -> {
                                fs.deleteFile(path);
                                refreshTable();
                            }));
                    menu.add(delete);
                }
            } else {
                JMenuItem newFile = new JMenuItem("新建文件");
                newFile.addActionListener(a -> createFileDialog(currentPath));
                menu.add(newFile);

                JMenuItem refresh = new JMenuItem("刷新");
                refresh.addActionListener(a -> refreshTable());
                menu.add(refresh);
            }
            if (menu.getComponentCount() > 0) {
                menu.show(table, e.getX(), e.getY());
            }
        }
    }
}
