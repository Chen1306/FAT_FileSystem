package gui;

import fs.FileSystem;

import javax.swing.*;

/**
 * 程序入口。
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                FileSystem fs = new FileSystem("disk.img");
                MainFrame frame = new MainFrame(fs);
                frame.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "启动失败: " + e.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                System.exit(1);
            }
        });
    }
}
