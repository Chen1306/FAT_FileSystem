package fs;

/**
 * 磁盘与文件系统的固定参数。
 */
public final class BootBlock {
    public static final int TOTAL_BLOCKS = 128;
    public static final int BLOCK_SIZE = 64;
    public static final int FAT_START_BLOCK = 0;
    public static final int FAT_BLOCKS = 2;
    public static final int ROOT_BLOCK = 2;
    public static final int DIR_ENTRY_SIZE = 8;
    public static final int ENTRIES_PER_BLOCK = BLOCK_SIZE / DIR_ENTRY_SIZE;
    public static final int MAX_OPEN_FILES = 5;

    public static final int FAT_FREE = 0;
    public static final int FAT_EOF = 255;
    public static final int FAT_BAD = 254;

    public static final int ATTR_READONLY = 0x01;
    public static final int ATTR_SYSTEM = 0x02;
    public static final int ATTR_NORMAL = 0x04;
    public static final int ATTR_DIRECTORY = 0x08;

    /** 空目录项的名称首字节。'$' 是文档禁止的名字字符，不会与合法名字冲突。 */
    public static final byte EMPTY_MARKER = (byte) '$';

    private BootBlock() {
    }
}
