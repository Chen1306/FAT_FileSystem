package gui;

import fs.FileSystem;

import javax.swing.*;

/**
 * 程序入口，读取虚拟磁盘
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                //读取文件，创建窗口
                FileSystem fs = new FileSystem("disk.img");
                MainFrame frame = new MainFrame(fs);
                frame.setVisible(true);
            } catch (Exception e) {
                //抛出启动宜昌
                JOptionPane.showMessageDialog(null,
                        "启动失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                System.exit(1);
            }
        });
    }
}
