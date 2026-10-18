package gui;

import javax.swing.table.DefaultTableModel;

/**
 * 文件列表表格模型，禁止直接编辑单元格。
 */
public class FileTableModel extends DefaultTableModel {
    public FileTableModel() {
        super(new String[]{"名称", "类型", "属性", "大小(块)", "起始块"}, 0);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
