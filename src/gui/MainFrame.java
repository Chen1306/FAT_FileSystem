package gui;

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
 * 最小原型主窗口：目录树、文件列表、日志区、右键菜单。
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

    public MainFrame(FileSystem fs) {
        this.fs = fs;
        setTitle("简易 FAT 文件系统模拟器（最小原型）");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

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
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(pathLabel, BorderLayout.NORTH);
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
        appendLog("文件系统已启动。右键目录树创建目录，右键表格空白处创建文件，双击文件写入内容。");
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

    private String promptContent() {
        JTextArea area = new JTextArea(8, 40);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(area),
                "输入要追加写入的内容", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return result == JOptionPane.OK_OPTION ? area.getText() : null;
    }

    private void writeFileDialog(DirItem item) {
        String content = promptContent();
        if (content == null) {
            return;
        }
        doAction("写入文件 " + item.getName(), () -> {
            String path = join(currentPath, item.getName());
            fs.openFile(path, OFT.WRITE);
            try {
                fs.writeFile(path, content);
            } finally {
                fs.closeFile(path);
            }
            refreshTable();
        });
    }

    private void doAction(String title, FsAction action) {
        try {
            action.run();
            appendLog(title + "：成功");
        } catch (Exception ex) {
            showError(title + "失败: " + ex.getMessage());
        }
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
            newFile.addActionListener(a -> {
                String name = promptName("新建文件");
                if (name == null) {
                    return;
                }
                doAction("新建文件", () -> {
                    fs.createFile(join(dir.path, name));
                    refreshTable();
                });
            });
            menu.add(newFile);

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
                    JMenuItem open = new JMenuItem("打开目录");
                    open.addActionListener(a -> navigateTo(join(currentPath, item.getName())));
                    menu.add(open);
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
                        appendLog("===== " + item.getName() + " 的内容 =====");
                        appendLog(content.isEmpty() ? "(空文件)" : content);
                    }));
                    menu.add(view);

                    JMenuItem write = new JMenuItem("写入内容");
                    write.addActionListener(a -> {
                        String content = promptContent();
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
                }
            } else {
                JMenuItem newFile = new JMenuItem("新建文件");
                newFile.addActionListener(a -> {
                    String name = promptName("新建文件");
                    if (name == null) {
                        return;
                    }
                    doAction("新建文件", () -> {
                        fs.createFile(join(currentPath, name));
                        refreshTable();
                    });
                });
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
