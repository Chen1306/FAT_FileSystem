package fs;

import java.nio.charset.StandardCharsets;

/**
 * 8 字节目录项：名称 3 + 类型 2 + 属性 1 + 起始盘块 1 + 长度 1。
 */
public class DirItem {
    private final String name;
    private int attribute;
    private final int startBlock;
    private int length;

    public DirItem(String name, int attribute, int startBlock, int length) {
        this.name = name;
        this.attribute = attribute;
        this.startBlock = startBlock;
        this.length = length;
    }

    public static boolean isEmpty(byte[] block, int offset) {
        return block[offset] == BootBlock.EMPTY_MARKER;
    }

    public static DirItem decode(byte[] block, int offset) {
        byte[] nameBytes = new byte[3];
        System.arraycopy(block, offset, nameBytes, 0, 3);
        String name = new String(nameBytes, StandardCharsets.US_ASCII).trim();
        int attribute = block[offset + 5] & 0xFF;
        int startBlock = block[offset + 6] & 0xFF;
        int length = block[offset + 7] & 0xFF;
        return new DirItem(name, attribute, startBlock, length);
    }

    public void encode(byte[] block, int offset) {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 3; i++) {
            block[offset + i] = i < nameBytes.length ? nameBytes[i] : (byte) ' ';
        }
        block[offset + 3] = ' ';
        block[offset + 4] = ' ';
        block[offset + 5] = (byte) attribute;
        block[offset + 6] = (byte) startBlock;
        block[offset + 7] = (byte) length;
    }

    public static void clear(byte[] block, int offset) {
        block[offset] = BootBlock.EMPTY_MARKER;
        block[offset + 1] = ' ';
        block[offset + 2] = ' ';
        block[offset + 3] = ' ';
        block[offset + 4] = ' ';
        block[offset + 5] = 0;
        block[offset + 6] = 0;
        block[offset + 7] = 0;
    }

    public boolean isDirectory() {
        return (attribute & BootBlock.ATTR_DIRECTORY) != 0;
    }

    public boolean isReadOnly() {
        return (attribute & BootBlock.ATTR_READONLY) != 0;
    }

    public String getTypeText() {
        if (isDirectory()) {
            return "目录";
        }
        if ((attribute & BootBlock.ATTR_READONLY) != 0) {
            return "只读文件";
        }
        if ((attribute & BootBlock.ATTR_SYSTEM) != 0) {
            return "系统文件";
        }
        if ((attribute & BootBlock.ATTR_NORMAL) != 0) {
            return "普通文件";
        }
        return "未知";
    }

    public String getName() {
        return name;
    }

    public int getAttribute() {
        return attribute;
    }

    public void setAttribute(int attribute) {
        this.attribute = attribute;
    }

    public int getStartBlock() {
        return startBlock;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
}
